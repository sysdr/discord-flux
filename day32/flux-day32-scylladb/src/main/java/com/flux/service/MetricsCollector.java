package com.flux.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free metrics collector using atomic operations.
 * Tracks write latencies, throughput, and error rates.
 */
public class MetricsCollector {
    private final java.util.concurrent.atomic.AtomicLong startTimeNanos = 
        new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
    private final LongAdder totalWrites = new LongAdder();
    private final LongAdder totalErrors = new LongAdder();
    private final Map<String, LatencyHistogram> latencies = new ConcurrentHashMap<>();
    
    public void recordWrite(long latencyNanos) {
        totalWrites.increment();
        getHistogram("write").record(latencyNanos);
    }
    
    public void recordError() {
        totalErrors.increment();
    }
    
    public long getTotalWrites() {
        return totalWrites.sum();
    }
    
    public long getTotalErrors() {
        return totalErrors.sum();
    }
    
    public LatencyHistogram getWriteLatency() {
        return getHistogram("write");
    }
    
    private LatencyHistogram getHistogram(String name) {
        return latencies.computeIfAbsent(name, k -> new LatencyHistogram());
    }
    
    public void reset() {
        totalWrites.reset();
        totalErrors.reset();
        latencies.clear();
        startTimeNanos.set(System.nanoTime());
    }
    
    public double getWritesPerSecond() {
        long elapsedNanos = System.nanoTime() - startTimeNanos.get();
        if (elapsedNanos <= 0) return 0;
        return totalWrites.sum() / (elapsedNanos / 1_000_000_000.0);
    }
    
    /**
     * Simple histogram for latency tracking.
     * Uses lock-free counters for each bucket.
     */
    public static class LatencyHistogram {
        private final LongAdder[] buckets = new LongAdder[10];
        private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong max = new AtomicLong(0);
        
        public LatencyHistogram() {
            for (int i = 0; i < buckets.length; i++) {
                buckets[i] = new LongAdder();
            }
        }
        
        public void record(long nanos) {
            long micros = nanos / 1000;
            
            // Update min/max
            min.updateAndGet(current -> Math.min(current, micros));
            max.updateAndGet(current -> Math.max(current, micros));
            
            // Bucket: 0-1ms, 1-2ms, 2-5ms, 5-10ms, 10-20ms, etc.
            int bucket;
            if (micros < 1000) bucket = 0;
            else if (micros < 2000) bucket = 1;
            else if (micros < 5000) bucket = 2;
            else if (micros < 10000) bucket = 3;
            else if (micros < 20000) bucket = 4;
            else if (micros < 50000) bucket = 5;
            else if (micros < 100000) bucket = 6;
            else if (micros < 200000) bucket = 7;
            else if (micros < 500000) bucket = 8;
            else bucket = 9;
            
            buckets[bucket].increment();
        }
        
        public long getP99() {
            long total = 0;
            for (var bucket : buckets) {
                total += bucket.sum();
            }
            
            long p99Threshold = (long) (total * 0.99);
            long cumulative = 0;
            
            for (int i = 0; i < buckets.length; i++) {
                cumulative += buckets[i].sum();
                if (cumulative >= p99Threshold) {
                    return getBucketUpperBound(i);
                }
            }
            
            return max.get();
        }
        
        private long getBucketUpperBound(int bucket) {
            return switch (bucket) {
                case 0 -> 1000;
                case 1 -> 2000;
                case 2 -> 5000;
                case 3 -> 10000;
                case 4 -> 20000;
                case 5 -> 50000;
                case 6 -> 100000;
                case 7 -> 200000;
                case 8 -> 500000;
                default -> max.get();
            };
        }
    }
}
