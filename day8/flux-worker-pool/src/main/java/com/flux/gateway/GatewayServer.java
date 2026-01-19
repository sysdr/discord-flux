package com.flux.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main Gateway server using NIO Selector pattern.
 * I/O thread handles socket events; workers process messages.
 */
public class GatewayServer {
    private static final int PORT = 8080;
    private static final int QUEUE_CAPACITY = 10_000;
    
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final WorkerPool workerPool;
    private final AtomicLong nextConnectionId = new AtomicLong();

    public GatewayServer() throws IOException {
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.bind(new InetSocketAddress(PORT));
        this.serverChannel.configureBlocking(false);
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        this.workerPool = new WorkerPool(QUEUE_CAPACITY);
        
        System.out.println("🚀 Gateway Server listening on port " + PORT);
        System.out.println("📊 Worker pool initialized (queue capacity: " + QUEUE_CAPACITY + ")");
    }

    public void start() {
        Thread.ofVirtual().name("selector-thread").start(() -> {
            while (true) {
                try {
                    selector.select(); // Block until events
                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    
                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();
                        
                        if (!key.isValid()) continue;
                        
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Selector error: " + e.getMessage());
                }
            }
        });
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        
        if (client != null) {
            client.configureBlocking(false);
            long connId = nextConnectionId.incrementAndGet();
            client.register(selector, SelectionKey.OP_READ, connId);
            
            Metrics.incrementCounter("connections.accepted");
            System.out.println("✅ Connection #" + connId + " from " + client.getRemoteAddress());
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        long connId = (Long) key.attachment();
        
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = client.read(buffer);
        
        if (bytesRead == -1) {
            // Client disconnected
            client.close();
            Metrics.incrementCounter("connections.closed");
            System.out.println("👋 Connection #" + connId + " closed");
            return;
        }
        
        if (bytesRead > 0) {
            buffer.flip();
            
            // Create task with sliced buffer (zero-copy)
            ByteBuffer payload = buffer.slice();
            Task task = new Task(
                connId,
                payload,
                System.nanoTime(),
                (InetSocketAddress) client.getRemoteAddress()
            );
            
            // Hand off to worker pool (non-blocking)
            workerPool.submit(task);
            
            Metrics.incrementCounter("messages.received");
        }
    }

    public WorkerPool getWorkerPool() {
        return workerPool;
    }

    public static void main(String[] args) throws IOException {
        GatewayServer server = new GatewayServer();
        server.start();
        
        // Start dashboard
        Dashboard dashboard = new Dashboard(server.getWorkerPool());
        dashboard.start();
        
        // Keep main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            server.workerPool.shutdown();
        }
    }
}
