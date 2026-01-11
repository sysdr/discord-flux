package com.flux.gateway;

import com.flux.dashboard.DashboardServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FluxGateway {
    
    private final int port;
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final HandshakeProcessor processor;
    private final ExecutorService virtualExecutor;
    private final Map<SelectionKey, ConnectionState> connections;
    private final Queue<PendingWrite> writeQueue;
    private final AtomicInteger activeConnections;
    private final GatewayMetrics metrics;
    
    private volatile boolean running = true;
    
    public record PendingWrite(SelectionKey key, ByteBuffer data) {}
    
    public FluxGateway(int port) throws IOException {
        this.port = port;
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.processor = new HandshakeProcessor();
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.connections = new ConcurrentHashMap<>();
        this.writeQueue = new ConcurrentLinkedQueue<>();
        this.activeConnections = new AtomicInteger(0);
        this.metrics = new GatewayMetrics();
        
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }
    
    public void start() throws IOException {
        System.out.println("🚀 Flux Gateway listening on port " + port);
        System.out.println("📊 Dashboard: http://localhost:8080/dashboard");
        
        // Start reaper thread for stale connections
        Thread.ofVirtual().start(this::reaperLoop);
        
        while (running) {
            selector.select(); // Block until events
            
            // Process pending writes from virtual threads
            processPendingWrites();
            
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                
                try {
                    if (!key.isValid()) continue;
                    
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                } catch (IOException e) {
                    closeConnection(key);
                }
            }
        }
    }
    
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            
            ConnectionState state = new ConnectionState(clientChannel);
            connections.put(clientKey, state);
            activeConnections.incrementAndGet();
            metrics.recordConnection();
            
            System.out.println("✅ Connection accepted: " + clientChannel.getRemoteAddress());
        }
    }
    
    private void handleRead(SelectionKey key) throws IOException {
        ConnectionState state = connections.get(key);
        if (state == null || state.phase() != ConnectionState.Phase.AWAITING_HEADERS) {
            return;
        }
        
        SocketChannel channel = state.channel();
        ByteBuffer buffer = state.readBuffer();
        
        int bytesRead = channel.read(buffer);
        
        if (bytesRead == -1) {
            closeConnection(key);
            return;
        }
        
        // Try to parse handshake
        var result = processor.parse(buffer);
        
        if (result.complete()) {
            state.phase(ConnectionState.Phase.COMPUTING_KEY);
            String clientKey = result.webSocketKey();
            
            // Offload SHA-1 to virtual thread
            virtualExecutor.submit(() -> {
                try {
                    String acceptKey = processor.computeAcceptKey(clientKey);
                    state.acceptKey(acceptKey);
                    state.phase(ConnectionState.Phase.READY_FOR_UPGRADE);
                    
                    ByteBuffer response = processor.createHandshakeResponse(acceptKey);
                    writeQueue.offer(new PendingWrite(key, response));
                    selector.wakeup(); // Wake selector to process write
                    
                    metrics.recordHandshake();
                } catch (Exception e) {
                    System.err.println("Handshake crypto failed: " + e.getMessage());
                    closeConnection(key);
                }
            });
        }
    }
    
    private void processPendingWrites() {
        PendingWrite write;
        while ((write = writeQueue.poll()) != null) {
            if (write.key().isValid()) {
                write.key().interestOps(SelectionKey.OP_WRITE);
            }
        }
    }
    
    private void handleWrite(SelectionKey key) throws IOException {
        ConnectionState state = connections.get(key);
        if (state == null || state.phase() != ConnectionState.Phase.READY_FOR_UPGRADE) {
            return;
        }
        
        SocketChannel channel = state.channel();
        ByteBuffer response = processor.createHandshakeResponse(state.acceptKey());
        
        channel.write(response);
        
        if (!response.hasRemaining()) {
            state.phase(ConnectionState.Phase.WEBSOCKET_ACTIVE);
            key.interestOps(SelectionKey.OP_READ); // Ready for WebSocket frames
            System.out.println("🔌 WebSocket upgraded: " + channel.getRemoteAddress());
        }
    }
    
    private void reaperLoop() {
        while (running) {
            try {
                Thread.sleep(5000); // Check every 5 seconds
                long now = System.currentTimeMillis();
                
                connections.forEach((key, state) -> {
                    if (state.phase() == ConnectionState.Phase.AWAITING_HEADERS && 
                        state.isStale(10000)) { // 10 second timeout
                        System.out.println("⚠️  Reaping stale connection");
                        closeConnection(key);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void closeConnection(SelectionKey key) {
        try {
            connections.remove(key);
            activeConnections.decrementAndGet();
            key.channel().close();
            key.cancel();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    public void shutdown() {
        running = false;
        virtualExecutor.shutdown();
        try {
            selector.close();
            serverChannel.close();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    public GatewayMetrics metrics() {
        return metrics;
    }
    
    public int activeConnections() {
        return activeConnections.get();
    }
    
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9001;
        FluxGateway gateway = new FluxGateway(port);
        
        // Start dashboard server
        Thread.ofVirtual().start(() -> {
            try {
                new DashboardServer(8080, gateway).start();
            } catch (IOException e) {
                System.err.println("Dashboard failed: " + e.getMessage());
            }
        });
        
        gateway.start();
    }
}
