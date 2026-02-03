package com.flux.integrationtest.metrics;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

/**
 * Lock-free latency aggregator using ring buffer and VarHandle atomics.
 * Computes percentiles (P50, P95, P99) without synchronized blocks.
 */
public class LatencyAggregator {
    private static final int CAPACITY = 1_000_000;
    private static final VarHandle WRITE_INDEX;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            WRITE_INDEX = lookup.findVarHandle(LatencyAggregator.class, "writeIndex", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private final long[] samples = new long[CAPACITY];
    private volatile long writeIndex = 0;
    
    public void record(long latencyNanos) {
        long index = (long) WRITE_INDEX.getAndAdd(this, 1L);
        samples[(int)(index % CAPACITY)] = latencyNanos;
    }
    
    public Percentiles calculate() {
        long totalSamples = (long) WRITE_INDEX.getAcquire(this);
        int validSamples = (int) Math.min(totalSamples, CAPACITY);
        
        if (validSamples == 0) {
            return new Percentiles(0, 0, 0, 0, 0);
        }
        
        long[] sorted = Arrays.copyOf(samples, validSamples);
        Arrays.sort(sorted);
        
        long p50 = sorted[validSamples / 2];
        long p95 = sorted[(int)(validSamples * 0.95)];
        long p99 = sorted[(int)(validSamples * 0.99)];
        long max = sorted[validSamples - 1];
        
        long sum = 0;
        for (int i = 0; i < validSamples; i++) {
            sum += sorted[i];
        }
        long avg = sum / validSamples;
        
        return new Percentiles(avg, p50, p95, p99, max);
    }
    
    public long getTotalSamples() {
        return (long) WRITE_INDEX.getAcquire(this);
    }
    
    public record Percentiles(
        long avgNanos,
        long p50Nanos,
        long p95Nanos,
        long p99Nanos,
        long maxNanos
    ) {
        public double avgMs() { return avgNanos / 1_000_000.0; }
        public double p50Ms() { return p50Nanos / 1_000_000.0; }
        public double p95Ms() { return p95Nanos / 1_000_000.0; }
        public double p99Ms() { return p99Nanos / 1_000_000.0; }
        public double maxMs() { return maxNanos / 1_000_000.0; }
    }
}
