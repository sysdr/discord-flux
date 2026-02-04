package com.flux.dashboard;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lock-free metrics collector for tracking ID generation performance.
 */
public class MetricsCollector {
    
    private static final VarHandle TOTAL_IDS;
    private static final VarHandle TOTAL_LATENCY_NS;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            TOTAL_IDS = lookup.findVarHandle(MetricsCollector.class, "totalIds", long.class);
            TOTAL_LATENCY_NS = lookup.findVarHandle(MetricsCollector.class, "totalLatencyNs", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private volatile long totalIds = 0L;
    private volatile long totalLatencyNs = 0L;
    private final ConcurrentLinkedQueue<Long> latencySamples = new ConcurrentLinkedQueue<>();
    private final long startTime = System.currentTimeMillis();
    
    private static final int MAX_SAMPLES = 10000;
    
    public void recordIdGeneration(long latencyNs) {
        TOTAL_IDS.getAndAdd(this, 1L);
        TOTAL_LATENCY_NS.getAndAdd(this, latencyNs);
        
        // Keep recent samples for percentile calculation
        latencySamples.offer(latencyNs);
        if (latencySamples.size() > MAX_SAMPLES) {
            latencySamples.poll();
        }
    }
    
    public long getTotalIds() {
        return (long) TOTAL_IDS.getAcquire(this);
    }
    
    public double getThroughput() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed == 0) return 0.0;
        return (double) getTotalIds() / (elapsed / 1000.0);
    }
    
    public double getAvgLatency() {
        long total = getTotalIds();
        if (total == 0) return 0.0;
        return (double) (long) TOTAL_LATENCY_NS.getAcquire(this) / total;
    }
    
    public double getP99Latency() {
        var samples = latencySamples.stream().sorted().toList();
        if (samples.isEmpty()) return 0.0;
        
        int p99Index = (int) (samples.size() * 0.99);
        return samples.get(Math.min(p99Index, samples.size() - 1));
    }
}
