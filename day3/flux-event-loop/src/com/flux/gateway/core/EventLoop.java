package com.flux.gateway.core;

import com.flux.gateway.protocol.ProtocolHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The heart of Flux: A single-threaded event loop using Java NIO Selector.
 * 
 * This demonstrates the Reactor pattern:
 * 1. Register channels with Selector
 * 2. Block on select() until I/O ready
 * 3. Process ready channels without blocking
 * 4. Repeat
 * 
 * Key metrics:
 * - Active connections: O(1) lookup via ConcurrentHashMap
 * - Memory: Fixed-size direct ByteBuffers per connection
 * - Latency: Single-digit milliseconds for message round-trip
 */
public class EventLoop implements Runnable {
    
    private final int port;
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final Map<SelectionKey, Connection> connections;
    private final ProtocolHandler protocolHandler;
    private final AtomicLong connectionIdGenerator;
    private volatile boolean running;
    
    // Metrics
    private final Metrics metrics;
    
    public EventLoop(int port) throws IOException {
        this.port = port;
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.connections = new ConcurrentHashMap<>();
        this.protocolHandler = new ProtocolHandler();
        this.connectionIdGenerator = new AtomicLong(0);
        this.running = true;
        this.metrics = new Metrics();
        
        // Configure server
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("✓ EventLoop bound to port " + port);
    }
    
    @Override
    public void run() {
        System.out.println("✓ EventLoop started");
        
        while (running) {
            try {
                // Block until at least one channel is ready
                int readyChannels = selector.select(100); // 100ms timeout
                
                if (readyChannels == 0) {
                    checkTimeouts();
                    continue;
                }
                
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();
                
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    try {
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
                
            } catch (IOException e) {
                System.err.println("EventLoop error: " + e.getMessage());
            }
        }
        
        cleanup();
    }
    
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        
        if (clientChannel == null) {
            return;
        }
        
        clientChannel.configureBlocking(false);
        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
        
        long connId = connectionIdGenerator.incrementAndGet();
        Connection conn = Connection.create(connId, clientChannel);
        connections.put(clientKey, conn);
        
        metrics.incrementConnections();
        System.out.println("✓ New connection #" + connId + " from " + clientChannel.getRemoteAddress());
    }
    
    private void handleRead(SelectionKey key) throws IOException {
        Connection conn = connections.get(key);
        if (conn == null) {
            return;
        }
        
        SocketChannel channel = conn.channel();
        ByteBuffer buffer = conn.readBuffer();
        
        int bytesRead = channel.read(buffer);
        
        if (bytesRead == -1) {
            // Client closed connection
            closeConnection(key);
            return;
        }
        
        if (bytesRead > 0) {
            metrics.addBytesRead(bytesRead);
            int messagesProcessed = protocolHandler.processIncomingData(conn);
            metrics.addMessagesProcessed(messagesProcessed);
            
            // If we have data to write, register for OP_WRITE
            if (!conn.writeQueue().isEmpty()) {
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
        }
    }
    
    private void handleWrite(SelectionKey key) throws IOException {
        Connection conn = connections.get(key);
        if (conn == null) {
            return;
        }
        
        SocketChannel channel = conn.channel();
        var writeQueue = conn.writeQueue();
        
        while (!writeQueue.isEmpty()) {
            ByteBuffer buffer = writeQueue.peek();
            if (buffer == null) {
                break;
            }
            
            int written = channel.write(buffer);
            metrics.addBytesWritten(written);
            
            if (buffer.hasRemaining()) {
                // Socket buffer full, wait for next write event
                return;
            }
            
            // Buffer fully written, remove from queue
            writeQueue.poll();
        }
        
        // No more data to write, remove OP_WRITE interest
        if (writeQueue.isEmpty()) {
            key.interestOps(SelectionKey.OP_READ);
        }
        
        // Check if connection is closing and all data sent
        if (conn.state() == ConnectionState.CLOSING && writeQueue.isEmpty()) {
            closeConnection(key);
        }
    }
    
    private void closeConnection(SelectionKey key) {
        Connection conn = connections.remove(key);
        if (conn != null) {
            try {
                conn.channel().close();
                metrics.decrementConnections();
                System.out.println("✗ Connection #" + conn.id() + " closed");
            } catch (IOException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
        key.cancel();
    }
    
    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        connections.values().stream()
            .filter(conn -> conn.state() == ConnectionState.HANDSHAKE)
            .filter(conn -> (now - conn.connectedAt()) > 30_000) // 30s timeout
            .forEach(conn -> {
                System.out.println("✗ Connection #" + conn.id() + " handshake timeout");
                conn.transitionTo(ConnectionState.CLOSING);
            });
    }
    
    public void stop() {
        running = false;
    }
    
    private void cleanup() {
        try {
            for (SelectionKey key : connections.keySet()) {
                closeConnection(key);
            }
            selector.close();
            serverChannel.close();
            System.out.println("✓ EventLoop stopped");
        } catch (IOException e) {
            System.err.println("Cleanup error: " + e.getMessage());
        }
    }
    
    public Metrics getMetrics() {
        return metrics;
    }
    
    /**
     * Thread-safe metrics holder
     */
    public static class Metrics {
        private final AtomicLong activeConnections = new AtomicLong(0);
        private final AtomicLong totalBytesRead = new AtomicLong(0);
        private final AtomicLong totalBytesWritten = new AtomicLong(0);
        private final AtomicLong messagesProcessed = new AtomicLong(0);
        
        void incrementConnections() { activeConnections.incrementAndGet(); }
        void decrementConnections() { activeConnections.decrementAndGet(); }
        void addBytesRead(long bytes) { totalBytesRead.addAndGet(bytes); }
        void addBytesWritten(long bytes) { totalBytesWritten.addAndGet(bytes); }
        void addMessagesProcessed(int count) { messagesProcessed.addAndGet(count); }
        
        public long getActiveConnections() { return activeConnections.get(); }
        public long getTotalBytesRead() { return totalBytesRead.get(); }
        public long getTotalBytesWritten() { return totalBytesWritten.get(); }
        public long getMessagesProcessed() { return messagesProcessed.get(); }
    }
}
