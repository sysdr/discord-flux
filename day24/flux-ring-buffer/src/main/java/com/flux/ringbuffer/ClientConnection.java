package com.flux.ringbuffer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a WebSocket client connection with output buffering.
 */
public class ClientConnection {
    private final String clientId;
    private final RingBuffer outputBuffer;
    private final AtomicBoolean connected = new AtomicBoolean(true);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong backpressureEvents = new AtomicLong(0);
    
    // Simulate varying client speeds
    private volatile long writeDelayNanos;
    
    public ClientConnection(String clientId, int bufferCapacity) {
        this.clientId = clientId;
        this.outputBuffer = new RingBuffer(bufferCapacity, clientId);
        this.writeDelayNanos = 0;  // No delay by default
    }
    
    /**
     * Attempt to enqueue an event for this client.
     */
    public boolean enqueue(GuildEvent event) {
        if (!connected.get()) {
            return false;
        }
        
        boolean success = outputBuffer.tryWrite(event);
        if (!success) {
            backpressureEvents.incrementAndGet();
        }
        return success;
    }
    
    /**
     * Simulate sending buffered messages to the client socket.
     * This would be called by an I/O thread in production.
     */
    public void flush() {
        BufferSlot slot;
        while ((slot = outputBuffer.tryRead()) != null) {
            // Simulate network I/O delay
            if (writeDelayNanos > 0) {
                long start = System.nanoTime();
                while (System.nanoTime() - start < writeDelayNanos) {
                    Thread.onSpinWait();
                }
            }
            
            // In production: socket.write(slot.payload().getBytes())
            messagesSent.incrementAndGet();
        }
    }
    
    public void setWriteDelayNanos(long delayNanos) {
        this.writeDelayNanos = delayNanos;
    }
    
    public void disconnect() {
        connected.set(false);
    }
    
    public boolean isConnected() {
        return connected.get();
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public int getBufferUtilization() {
        return outputBuffer.utilizationPercent();
    }
    
    public long getMessagesSent() {
        return messagesSent.get();
    }
    
    public long getBackpressureEvents() {
        return backpressureEvents.get();
    }
    
    public long getDroppedMessages() {
        return outputBuffer.getDroppedCount();
    }
    
    public long getBufferedMessages() {
        return outputBuffer.size();
    }
}
