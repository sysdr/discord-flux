package com.flux.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class Metrics {
    private final AtomicLong publishedEvents = new AtomicLong();
    private final AtomicLong throttledEvents = new AtomicLong();
    private final AtomicLong droppedEvents = new AtomicLong();
    
    public void incrementPublished() {
        publishedEvents.incrementAndGet();
    }
    
    public void incrementThrottled() {
        throttledEvents.incrementAndGet();
    }
    
    public void incrementDropped() {
        droppedEvents.incrementAndGet();
    }
    
    public long getPublished() {
        return publishedEvents.get();
    }
    
    public long getThrottled() {
        return throttledEvents.get();
    }
    
    public long getDropped() {
        return droppedEvents.get();
    }
    
    public void reset() {
        publishedEvents.set(0);
        throttledEvents.set(0);
        droppedEvents.set(0);
    }
}
