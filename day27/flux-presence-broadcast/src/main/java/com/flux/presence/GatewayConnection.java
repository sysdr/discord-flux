package com.flux.presence;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single WebSocket client connection.
 * Each connection has its own ring buffer for backpressure isolation.
 */
public class GatewayConnection {
    private final long userId;
    private final ConnectionRingBuffer ringBuffer;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());
    
    // Current presence state
    private volatile PresenceStatus currentStatus = PresenceStatus.ONLINE;
    
    public GatewayConnection(long userId, int ringBufferSize) {
        this.userId = userId;
        this.ringBuffer = new ConnectionRingBuffer(ringBufferSize);
    }
    
    public long getUserId() {
        return userId;
    }
    
    public ConnectionRingBuffer getRingBuffer() {
        return ringBuffer;
    }
    
    public boolean isActive() {
        return active.get();
    }
    
    public void close() {
        active.set(false);
    }
    
    public void updateActivity() {
        lastActivityTime.set(System.currentTimeMillis());
    }
    
    public long getLastActivityTime() {
        return lastActivityTime.get();
    }
    
    public PresenceStatus getCurrentStatus() {
        return currentStatus;
    }
    
    public void setCurrentStatus(PresenceStatus status) {
        this.currentStatus = status;
    }
    
    /**
     * Simulated send - in production this would write to SocketChannel.
     */
    public void sendMessage(ByteBuffer message) {
        // In real implementation: socketChannel.write(message)
        updateActivity();
    }
    
    @Override
    public String toString() {
        return "GatewayConnection{userId=" + userId + 
               ", status=" + currentStatus + 
               ", ringSize=" + ringBuffer.size() + 
               ", dropped=" + ringBuffer.getDroppedCount() + "}";
    }
}
