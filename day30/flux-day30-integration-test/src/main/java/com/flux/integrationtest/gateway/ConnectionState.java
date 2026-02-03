package com.flux.integrationtest.gateway;

import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-connection state tracking.
 * Uses atomic operations for thread-safe updates without locks.
 */
public class ConnectionState {
    private final long userId;
    private final SocketChannel socket;
    private final ByteBuffer readBuffer;
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());
    private final AtomicReference<HealthStatus> health = new AtomicReference<>(HealthStatus.HEALTHY);
    
    public enum HealthStatus {
        HEALTHY,
        SLOW_CONSUMER,
        DISCONNECTED
    }
    
    public ConnectionState(long userId, SocketChannel socket) {
        this.userId = userId;
        this.socket = socket;
        this.readBuffer = ByteBuffer.allocateDirect(8192);
    }
    
    public long getUserId() { return userId; }
    public SocketChannel getSocket() { return socket; }
    public ByteBuffer getReadBuffer() { return readBuffer; }
    
    public void incrementSent() { messagesSent.incrementAndGet(); }
    public void incrementReceived() { messagesReceived.incrementAndGet(); }
    public void updateHeartbeat() { lastHeartbeat.set(System.currentTimeMillis()); }
    
    public long getMessagesSent() { return messagesSent.get(); }
    public long getMessagesReceived() { return messagesReceived.get(); }
    public long getLastHeartbeat() { return lastHeartbeat.get(); }
    
    public HealthStatus getHealth() { return health.get(); }
    public void setHealth(HealthStatus status) { health.set(status); }
    
    public boolean isHealthy() { return health.get() == HealthStatus.HEALTHY; }
    public boolean isTimedOut(long timeoutMs) {
        return System.currentTimeMillis() - lastHeartbeat.get() > timeoutMs;
    }
}
