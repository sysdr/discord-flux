package com.flux.backpressure;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NIO-based Gateway Server with backpressure detection.
 * Uses Selector for non-blocking I/O and per-connection ring buffers.
 */
public class GatewayServer {
    private final int port;
    private final BackpressureMetrics metrics;
    private final ConcurrentHashMap<Integer, Connection> connections;
    private final AtomicInteger nextConnectionId;
    private final AtomicBoolean running;
    
    private Selector selector;
    private ServerSocketChannel serverChannel;
    
    public GatewayServer(int port) {
        this.port = port;
        this.metrics = new BackpressureMetrics();
        this.connections = new ConcurrentHashMap<>();
        this.nextConnectionId = new AtomicInteger(0);
        this.running = new AtomicBoolean(true);
    }
    
    public void start() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("[GatewayServer] Listening on 0.0.0.0:" + port);
        
        // Enable demo mode for visualization
        metrics.setDemoMode(true);
        
        // Start dashboard in separate thread
        Thread dashboardThread = Thread.ofVirtual().start(() -> {
            try {
                Dashboard dashboard = new Dashboard(8080, this);
                dashboard.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        
        // Start broadcast simulator in separate thread
        Thread broadcastThread = Thread.ofVirtual().start(this::broadcastLoop);
        
        // Main Selector event loop
        while (running.get()) {
            try {
                selector.select(100); // 100ms timeout
                
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    if (key.isAcceptable()) {
                        handleAccept();
                    } else if (key.isReadable()) {
                        Connection conn = (Connection) key.attachment();
                        conn.handleRead();
                    } else if (key.isWritable()) {
                        Connection conn = (Connection) key.attachment();
                        conn.handleWritable();
                    }
                }
            } catch (IOException e) {
                System.err.println("[GatewayServer] Selector error: " + e.getMessage());
            }
        }
    }
    
    private void handleAccept() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel == null) {
            return;
        }
        
        clientChannel.configureBlocking(false);
        clientChannel.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
        clientChannel.setOption(java.net.StandardSocketOptions.SO_SNDBUF, 2048); // 2KB (small to trigger buffering faster)
        
        int connectionId = nextConnectionId.getAndIncrement();
        Connection connection = new Connection(connectionId, clientChannel, selector, metrics);
        
        SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ, connection);
        connection.setKey(key);
        
        connections.put(connectionId, connection);
        
        System.out.println("[GatewayServer] New connection #" + connectionId + 
                          " (total: " + connections.size() + ")");
    }
    
    /**
     * Broadcast loop: sends messages to all connections at 1000 msg/sec for demo.
     */
    private void broadcastLoop() {
        long messageCounter = 0;
        
        while (running.get()) {
            try {
                Thread.sleep(1); // 1000 messages/second (1ms interval)
                
                String messageText = "Message #" + messageCounter++;
                ByteBuffer message = ByteBuffer.wrap(messageText.getBytes(StandardCharsets.UTF_8));
                
                // Broadcast to all active connections
                for (Connection conn : connections.values()) {
                    if (conn.isConnected()) {
                        conn.broadcast(message.duplicate());
                    }
                }
                
                // Clean up disconnected connections
                connections.values().removeIf(conn -> !conn.isConnected());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    public BackpressureMetrics getMetrics() {
        return metrics;
    }
    
    public ConcurrentHashMap<Integer, Connection> getConnections() {
        return connections;
    }
    
    public void shutdown() {
        running.set(false);
        try {
            if (selector != null) {
                selector.close();
            }
            if (serverChannel != null) {
                serverChannel.close();
            }
        } catch (IOException e) {
            System.err.println("[GatewayServer] Shutdown error: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) throws IOException {
        GatewayServer server = new GatewayServer(9090);
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            System.out.println("\n[GatewayServer] Shutting down...");
            server.shutdown();
        }));
        
        server.start();
    }
}
