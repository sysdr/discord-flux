package com.flux.backpressure;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * Represents a single client connection with backpressure handling.
 */
public class Connection {
    private final int id;
    private final SocketChannel channel;
    private final RingBuffer outboundBuffer;
    private final BackpressureMetrics metrics;
    private final Selector selector;
    private SelectionKey key;
    
    private long backpressureStartTime = 0;
    private static final long SLOW_CONSUMER_THRESHOLD_NS = 5_000_000_000L; // 5 seconds
    
    private volatile boolean connected = true;
    
    public Connection(int id, SocketChannel channel, Selector selector, BackpressureMetrics metrics) {
        this.id = id;
        this.channel = channel;
        this.selector = selector;
        this.metrics = metrics;
        this.outboundBuffer = new RingBuffer(256); // 256 slots = 256KB max buffer
    }
    
    public void setKey(SelectionKey key) {
        this.key = key;
    }
    
    /**
     * Broadcast a message to this connection. Handles backpressure.
     */
    public void broadcast(ByteBuffer message) {
        if (!connected) {
            return;
        }
        
        metrics.recordWriteAttempt();
        
        // Fast path: try immediate write if buffer is empty
        if (outboundBuffer.isEmpty()) {
            try {
                int written = channel.write(message.duplicate());
                if (written == message.remaining()) {
                    // Complete write: success
                    metrics.recordWriteSuccess();
                    metrics.updateBufferUtilization(id, 0); // dashboard: show connection as active
                    return; // Complete write, no buffering needed
                } else if (written > 0) {
                    // Partial write: buffer the remainder
                    message.position(message.position() + written);
                    boolean buffered = outboundBuffer.offer(message);
                    if (buffered) {
                        metrics.recordMessageBuffered();
                    } else {
                        // Ring buffer full even after partial write
                        if (backpressureStartTime == 0) {
                            backpressureStartTime = System.nanoTime();
                            metrics.recordBackpressureEvent();
                            System.out.println("[Connection:" + id + "] BACKPRESSURE: buffer full");
                        }
                    }
                } else {
                    // written == 0: socket send buffer full, must buffer entire message
                    boolean buffered = outboundBuffer.offer(message);
                    if (buffered) {
                        metrics.recordMessageBuffered();
                    } else {
                        // Ring buffer full
                        if (backpressureStartTime == 0) {
                            backpressureStartTime = System.nanoTime();
                            metrics.recordBackpressureEvent();
                            System.out.println("[Connection:" + id + "] BACKPRESSURE: buffer full");
                        }
                    }
                }
            } catch (IOException e) {
                disconnect("Write error: " + e.getMessage());
                return;
            }
        } else {
            // Slow path: enqueue to ring buffer
            boolean buffered = outboundBuffer.offer(message);
            
            if (!buffered) {
                // Buffer full: start tracking backpressure time
                if (backpressureStartTime == 0) {
                    backpressureStartTime = System.nanoTime();
                    metrics.recordBackpressureEvent();
                    System.out.println("[Connection:" + id + "] BACKPRESSURE: buffer full");
                }
                
                // Check slow consumer threshold
                long backpressureDuration = System.nanoTime() - backpressureStartTime;
                if (backpressureDuration > SLOW_CONSUMER_THRESHOLD_NS) {
                    disconnect("SLOW_CONSUMER: evicted after " + 
                              (backpressureDuration / 1_000_000_000.0) + "s in backpressure");
                    metrics.recordSlowConsumerEviction();
                    return;
                }
            } else {
                metrics.recordMessageBuffered();
            }
        }
        
        // Update metrics for dashboard
        metrics.updateBufferUtilization(id, outboundBuffer.getUtilization());
        
        // Register write interest if not already registered
        registerWriteInterest();
    }
    
    /**
     * Handle write-ready event from Selector.
     */
    public void handleWritable() {
        while (true) {
            ByteBuffer pending = outboundBuffer.peek();
            if (pending == null) {
                // Buffer drained: remove write interest
                deregisterWriteInterest();
                backpressureStartTime = 0; // reset backpressure timer
                metrics.updateBufferUtilization(id, 0);
                break;
            }
            
            try {
                int written = channel.write(pending.duplicate());
                if (written == pending.remaining()) {
                    // Complete write: remove from buffer
                    outboundBuffer.poll();
                    metrics.recordWriteSuccess();
                    metrics.updateBufferUtilization(id, outboundBuffer.getUtilization());
                } else {
                    // Partial write: socket buffer still full
                    break;
                }
            } catch (IOException e) {
                disconnect("Write error during drain: " + e.getMessage());
                break;
            }
        }
    }
    
    /**
     * Handle read-ready event (dummy implementation for demo).
     */
    public void handleRead() {
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        try {
            int bytesRead = channel.read(readBuffer);
            if (bytesRead == -1) {
                disconnect("Client closed connection");
            }
        } catch (IOException e) {
            disconnect("Read error: " + e.getMessage());
        }
    }
    
    private void registerWriteInterest() {
        if (key != null && key.isValid()) {
            int currentOps = key.interestOps();
            if ((currentOps & SelectionKey.OP_WRITE) == 0) {
                key.interestOps(currentOps | SelectionKey.OP_WRITE);
            }
        }
    }
    
    private void deregisterWriteInterest() {
        if (key != null && key.isValid()) {
            int currentOps = key.interestOps();
            if ((currentOps & SelectionKey.OP_WRITE) != 0) {
                key.interestOps(currentOps & ~SelectionKey.OP_WRITE);
            }
        }
    }
    
    public void disconnect(String reason) {
        if (!connected) {
            return;
        }
        
        connected = false;
        System.out.println("[Connection:" + id + "] DISCONNECT: " + reason);
        
        try {
            if (key != null) {
                key.cancel();
            }
            channel.close();
        } catch (IOException e) {
            // Ignore close errors
        }
        
        metrics.removeConnection(id);
    }
    
    public boolean isConnected() {
        return connected && channel.isConnected();
    }
    
    public int getId() {
        return id;
    }
    
    public int getBufferUtilization() {
        return outboundBuffer.getUtilization();
    }
}
