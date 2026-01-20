package com.flux.serialization.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class Metrics {
    private final String engineName;
    private final LongAdder totalOperations = new LongAdder();
    private final LongAdder totalLatencyNanos = new LongAdder();
    private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatency = new AtomicLong(0);
    private final long[] latencyHistogram = new long[10000]; // up to 10µs buckets
    private final long startTime;
    
    public Metrics(String engineName) {
        this.engineName = engineName;
        this.startTime = System.nanoTime();
    }
    
    public void recordLatency(long nanos) {
        totalOperations.increment();
        totalLatencyNanos.add(nanos);
        
        // Update min/max
        minLatency.updateAndGet(min -> Math.min(min, nanos));
        maxLatency.updateAndGet(max -> Math.max(max, nanos));
        
        // Histogram (1µs buckets, capped at 10ms)
        int bucket = (int)Math.min(nanos / 1000, latencyHistogram.length - 1);
        synchronized(latencyHistogram) {
            latencyHistogram[bucket]++;
        }
    }
    
    public String getName() {
        return engineName;
    }
    
    public long getTotalOperations() {
        return totalOperations.sum();
    }
    
    public double getAverageLatencyMicros() {
        long ops = totalOperations.sum();
        return ops == 0 ? 0 : (totalLatencyNanos.sum() / (double)ops) / 1000.0;
    }
    
    public long getMinLatencyMicros() {
        long min = minLatency.get();
        return min == Long.MAX_VALUE ? 0 : min / 1000;
    }
    
    public long getMaxLatencyMicros() {
        return maxLatency.get() / 1000;
    }
    
    public double getThroughput() {
        long elapsedNanos = System.nanoTime() - startTime;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        return totalOperations.sum() / elapsedSeconds;
    }
    
    public long getP99LatencyMicros() {
        return getPercentile(0.99);
    }
    
    private long getPercentile(double percentile) {
        long total = totalOperations.sum();
        if (total == 0) return 0;
        
        long target = (long)(total * percentile);
        long count = 0;
        
        synchronized(latencyHistogram) {
            for (int i = 0; i < latencyHistogram.length; i++) {
                count += latencyHistogram[i];
                if (count >= target) {
                    return i; // µs
                }
            }
        }
        return latencyHistogram.length - 1;
    }
}
