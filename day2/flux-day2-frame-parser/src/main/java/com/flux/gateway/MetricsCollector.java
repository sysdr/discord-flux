package com.flux.gateway;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics collector using atomic counters.
 */
public class MetricsCollector {
    
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong totalFrames = new AtomicLong(0);
    private final AtomicLong bytesReceived = new AtomicLong(0);

    public void incrementConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementConnections() {
        activeConnections.decrementAndGet();
    }

    public void incrementFrames() {
        totalFrames.incrementAndGet();
    }

    public void addBytes(long bytes) {
        bytesReceived.addAndGet(bytes);
    }

    public long getActiveConnections() {
        return activeConnections.get();
    }

    public long getTotalFrames() {
        return totalFrames.get();
    }

    public long getBytesReceived() {
        return bytesReceived.get();
    }

    public String getStats() {
        return String.format(
            "Connections: %d | Frames: %d | Bytes: %d",
            activeConnections.get(), totalFrames.get(), bytesReceived.get()
        );
    }
}
