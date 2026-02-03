package com.flux.typing;

import com.flux.metrics.Metrics;

public class TypingIndicatorService {
    private final TypingEventRing ring;
    private final ThrottleGate throttle;
    private final Metrics metrics;
    
    public TypingIndicatorService(Metrics metrics) {
        this.ring = new TypingEventRing();
        this.throttle = new ThrottleGate();
        this.metrics = metrics;
    }
    
    public boolean handleTypingEvent(long userId, long channelId) {
        if (!throttle.tryAcquire(userId)) {
            metrics.incrementThrottled();
            return false;
        }
        
        ring.publish(userId, channelId);
        metrics.incrementPublished();
        return true;
    }
    
    public long[] getActiveTypers(long channelId) {
        long[] result = new long[100]; // Max 100 concurrent typers per channel
        int[] count = new int[1];
        
        ring.collectActiveTypers(channelId, result, count);
        
        long[] trimmed = new long[count[0]];
        System.arraycopy(result, 0, trimmed, 0, count[0]);
        return trimmed;
    }
    
    public TypingEventRing getRing() {
        return ring;
    }
    
    public void periodicCleanup() {
        throttle.cleanup(30_000_000_000L); // Cleanup throttle states older than 30 sec
    }
}
