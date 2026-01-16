package com.flux.gateway;

import java.util.concurrent.atomic.AtomicLong;

public final class MetricsCollector {
    private final AtomicLong heartbeatsReceived;
    private final AtomicLong heartbeatsSent;
    private final AtomicLong connectionsAccepted;
    private final AtomicLong connectionsClosed;
    
    public MetricsCollector() {
        this.heartbeatsReceived = new AtomicLong(0);
        this.heartbeatsSent = new AtomicLong(0);
        this.connectionsAccepted = new AtomicLong(0);
        this.connectionsClosed = new AtomicLong(0);
    }
    
    public void recordHeartbeatReceived() {
        heartbeatsReceived.incrementAndGet();
    }
    
    public void recordHeartbeatSent() {
        heartbeatsSent.incrementAndGet();
    }
    
    public void recordConnectionAccepted() {
        connectionsAccepted.incrementAndGet();
    }
    
    public void recordConnectionClosed() {
        connectionsClosed.incrementAndGet();
    }
    
    public Metrics snapshot() {
        return new Metrics(
            heartbeatsReceived.get(),
            heartbeatsSent.get(),
            connectionsAccepted.get(),
            connectionsClosed.get()
        );
    }
    
    public record Metrics(
        long heartbeatsReceived,
        long heartbeatsSent,
        long connectionsAccepted,
        long connectionsClosed
    ) {}
}
