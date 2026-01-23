package com.flux.gateway;

import java.io.IOException;
import java.lang.management.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import com.sun.management.HotSpotDiagnosticMXBean;

/**
 * Intentionally leaky WebSocket gateway for memory profiling education.
 * Contains 4 deliberate leak patterns found in production systems.
 */
public class LeakyGateway implements Runnable {
    
    // LEAK #1: Sessions never removed on disconnect
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    
    private final ServerSocketChannel serverChannel;
    private final Selector selector;
    private final LeakDetector leakDetector;
    private final MetricsCollector metrics;
    private volatile boolean running = true;
    
    private final AtomicLong connectionCounter = new AtomicLong(0);
    
    public LeakyGateway(int port) throws IOException {
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.bind(new InetSocketAddress(port));
        this.serverChannel.configureBlocking(false);
        
        this.selector = Selector.open();
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        this.leakDetector = new LeakDetector();
        this.metrics = new MetricsCollector();
        
        // Start metrics collector thread
        Thread.ofVirtual().name("metrics-collector").start(metrics);
        
        // Start heap dump monitor
        Thread.ofVirtual().name("heap-monitor").start(this::monitorHeap);
        
        System.out.println("[LeakyGateway] Server started on port " + port);
        System.out.println("[LeakyGateway] JVM: " + System.getProperty("java.version"));
        System.out.println("[LeakyGateway] Max Heap: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB");
    }
    
    @Override
    public void run() {
        while (running) {
            try {
                selector.select(1000);
                
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    
                    if (!key.isValid()) continue;
                    
                    if (key.isAcceptable()) {
                        handleAccept();
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
                
            } catch (IOException e) {
                System.err.println("[LeakyGateway] Selector error: " + e.getMessage());
            }
        }
    }
    
    private void handleAccept() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            
            String sessionId = "session-" + connectionCounter.incrementAndGet();
            Session session = new Session(sessionId, clientChannel);
            
            // LEAK #1: Add to map but never remove
            sessions.put(sessionId, session);
            
            // Track with leak detector
            leakDetector.track(sessionId);
            
            // LEAK #4: Register listener with closure capturing 'this'
            GlobalListenerRegistry.register(session, msg -> {
                // This lambda captures 'this' (LeakyGateway), creating circular reference
                this.metrics.incrementMessageCount();
            });
            
            clientChannel.register(selector, SelectionKey.OP_READ, session);
            
            System.out.println("[LeakyGateway] New connection: " + sessionId + 
                " (Total in map: " + sessions.size() + ")");
        }
    }
    
    private void handleRead(SelectionKey key) {
        Session session = (Session) key.attachment();
        
        try {
            // LEAK #3: ThreadLocal accumulation
            ByteBuffer parseBuffer = FrameParser.getParseBuffer();
            
            int bytesRead = session.channel().read(parseBuffer);
            
            if (bytesRead == -1) {
                // Client disconnected
                System.out.println("[LeakyGateway] Client disconnected: " + session.id() + 
                    " (channel closed, but session remains in map!)");
                key.cancel();
                session.channel().close();
                
                // BUG: We DON'T remove from sessions map!
                // sessions.remove(session.id()); // ← This should happen but doesn't
                
                // BUG: We DON'T clean up ThreadLocal!
                // FrameParser.cleanup(); // ← This should happen but doesn't
                
                return;
            }
            
            if (bytesRead > 0) {
                parseBuffer.flip();
                
                // Echo back
                session.channel().write(parseBuffer);
                
                parseBuffer.clear();
                metrics.incrementMessageCount();
            }
            
        } catch (IOException e) {
            System.err.println("[LeakyGateway] Read error on " + session.id() + ": " + e.getMessage());
            key.cancel();
        }
    }
    
    private void monitorHeap() {
        while (running) {
            try {
                Thread.sleep(10000); // Check every 10 seconds
                
                MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
                MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
                
                long used = heapUsage.getUsed();
                long max = heapUsage.getMax();
                double utilization = (double) used / max;
                
                System.out.printf("[HeapMonitor] Heap: %d MB / %d MB (%.1f%%)%n",
                    used / 1024 / 1024, max / 1024 / 1024, utilization * 100);
                
                // Check Old Gen specifically
                for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                    if (pool.getType() == MemoryType.HEAP && 
                        pool.getName().contains("Old Gen")) {
                        MemoryUsage oldGenUsage = pool.getUsage();
                        double oldGenUtil = (double) oldGenUsage.getUsed() / oldGenUsage.getMax();
                        
                        if (oldGenUtil > 0.7) {
                            System.out.println("[HeapMonitor] WARNING: Old Gen > 70%, triggering heap dump");
                            dumpHeap();
                        }
                    }
                }
                
                // Run leak detection
                leakDetector.verify();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void dumpHeap() {
        try {
            HotSpotDiagnosticMXBean mxBean = ManagementFactory.getPlatformMXBean(
                HotSpotDiagnosticMXBean.class
            );
            
            String filename = "/tmp/heap-" + System.currentTimeMillis() + ".hprof";
            System.out.println("[HeapMonitor] Dumping heap to: " + filename);
            
            mxBean.dumpHeap(filename, true); // true = live objects only
            
            System.out.println("[HeapMonitor] Heap dump complete: " + filename);
            System.out.println("[HeapMonitor] Analyze with: mat.sh " + filename);
            
        } catch (IOException e) {
            System.err.println("[HeapMonitor] Heap dump failed: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        running = false;
        try {
            selector.close();
            serverChannel.close();
        } catch (IOException e) {
            System.err.println("Shutdown error: " + e.getMessage());
        }
    }
    
    public Map<String, Session> getSessions() {
        return sessions;
    }
    
    public MetricsCollector getMetrics() {
        return metrics;
    }
    
    public LeakDetector getLeakDetector() {
        return leakDetector;
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9000;
        
        LeakyGateway gateway = new LeakyGateway(port);
        
        // Start dashboard
        Dashboard dashboard = new Dashboard(8080, gateway);
        Thread.ofVirtual().name("dashboard").start(dashboard);
        
        // Start gateway
        Thread.ofVirtual().name("gateway").start(gateway);
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Main] Shutting down...");
            gateway.shutdown();
            dashboard.shutdown();
        }));
        
        System.out.println("[Main] Gateway running. Dashboard: http://localhost:8080/dashboard");
        
        // Keep main thread alive
        Thread.currentThread().join();
    }
}
