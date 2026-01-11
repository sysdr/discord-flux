package com.flux.gateway;

import java.util.concurrent.atomic.AtomicLong;

public class GatewayMetrics {
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong totalHandshakes = new AtomicLong(0);
    private final AtomicLong handshakeErrors = new AtomicLong(0);
    
    public void recordConnection() {
        totalConnections.incrementAndGet();
    }
    
    public void recordHandshake() {
        totalHandshakes.incrementAndGet();
    }
    
    public void recordError() {
        handshakeErrors.incrementAndGet();
    }
    
    public long totalConnections() { return totalConnections.get(); }
    public long totalHandshakes() { return totalHandshakes.get(); }
    public long handshakeErrors() { return handshakeErrors.get(); }
}
