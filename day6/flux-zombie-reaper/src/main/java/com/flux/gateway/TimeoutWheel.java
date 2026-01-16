package com.flux.gateway;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class TimeoutWheel {
    private static final int SLOTS = 60; // 60-second timeout window
    
    private final ConcurrentHashMap<Integer, Set<String>> buckets;
    private final AtomicInteger currentSlot;
    private final AtomicLong totalScheduled;
    private final AtomicLong totalExpired;
    
    public TimeoutWheel() {
        this.buckets = new ConcurrentHashMap<>(SLOTS);
        this.currentSlot = new AtomicInteger(0);
        this.totalScheduled = new AtomicLong(0);
        this.totalExpired = new AtomicLong(0);
        
        // Pre-allocate all buckets
        for (int i = 0; i < SLOTS; i++) {
            buckets.put(i, ConcurrentHashMap.newKeySet());
        }
    }
    
    /**
     * Schedule a connection to expire in the specified number of seconds.
     * This removes the connection from any previous slot first.
     */
    public void schedule(String connectionId, int timeoutSeconds) {
        if (timeoutSeconds <= 0 || timeoutSeconds > SLOTS) {
            throw new IllegalArgumentException("Timeout must be between 1 and " + SLOTS);
        }
        
        // Remove from all buckets (inefficient but simple for demo)
        // In production, track which bucket each connection is in
        removeFromAllBuckets(connectionId);
        
        // Calculate expiry slot
        int expirySlot = (currentSlot.get() + timeoutSeconds) % SLOTS;
        buckets.get(expirySlot).add(connectionId);
        totalScheduled.incrementAndGet();
    }
    
    /**
     * Advance the wheel by one slot and return all expired connections.
     */
    public Set<String> advance() {
        int newSlot = currentSlot.updateAndGet(n -> (n + 1) % SLOTS);
        Set<String> expiredBucket = buckets.get(newSlot);
        
        // Copy to avoid concurrent modification during iteration
        Set<String> expired = new HashSet<>(expiredBucket);
        expiredBucket.clear(); // Reuse the bucket
        
        totalExpired.addAndGet(expired.size());
        return expired;
    }
    
    /**
     * Get current statistics for monitoring.
     */
    public WheelStats getStats() {
        int[] distribution = new int[SLOTS];
        int totalConnections = 0;
        
        for (int i = 0; i < SLOTS; i++) {
            int size = buckets.get(i).size();
            distribution[i] = size;
            totalConnections += size;
        }
        
        return new WheelStats(
            currentSlot.get(),
            totalConnections,
            totalScheduled.get(),
            totalExpired.get(),
            distribution
        );
    }
    
    private void removeFromAllBuckets(String connectionId) {
        for (Set<String> bucket : buckets.values()) {
            bucket.remove(connectionId);
        }
    }
    
    public record WheelStats(
        int currentSlot,
        int activeConnections,
        long totalScheduled,
        long totalExpired,
        int[] bucketDistribution
    ) {}
}
