package com.flux.gateway;

import java.util.concurrent.atomic.AtomicLong;

public final class Metrics {
    private final AtomicLong heartbeatsSent = new AtomicLong(0);
    private final AtomicLong acksReceived = new AtomicLong(0);
    private final AtomicLong timeouts = new AtomicLong(0);
    
    public void incrementHeartbeatsSent() {
        heartbeatsSent.incrementAndGet();
    }
    
    public void incrementAcksReceived() {
        acksReceived.incrementAndGet();
    }
    
    public void recordTimeouts(int count) {
        timeouts.addAndGet(count);
    }
    
    public long getHeartbeatsSent() {
        return heartbeatsSent.get();
    }
    
    public long getAcksReceived() {
        return acksReceived.get();
    }
    
    public long getTimeouts() {
        return timeouts.get();
    }
    
    public String toJson() {
        return """
            {
                "heartbeatsSent": %d,
                "acksReceived": %d,
                "timeouts": %d
            }
            """.formatted(heartbeatsSent.get(), acksReceived.get(), timeouts.get());
    }
}
