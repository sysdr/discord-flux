package com.flux.gateway;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class Metrics {
    private final LongAdder identifyCount = new LongAdder();
    private final LongAdder resumeSuccessCount = new LongAdder();
    private final LongAdder resumeFailedCount = new LongAdder();
    private final AtomicLong totalResumeLatencyMs = new AtomicLong(0);
    private final LongAdder resumeLatencySamples = new LongAdder();
    
    // Resume latency histogram (ms buckets)
    private final LongAdder[] latencyBuckets = new LongAdder[10];
    private static final double[] BUCKET_BOUNDS = {1, 5, 10, 20, 50, 100, 200, 500, 1000, 5000};
    
    public Metrics() {
        for (int i = 0; i < latencyBuckets.length; i++) {
            latencyBuckets[i] = new LongAdder();
        }
    }
    
    public void incrementIdentify() {
        identifyCount.increment();
    }
    
    public void incrementResumeSuccess() {
        resumeSuccessCount.increment();
    }
    
    public void incrementResumeFailed() {
        resumeFailedCount.increment();
    }
    
    public void recordResumeLatency(double latencyMs) {
        totalResumeLatencyMs.addAndGet((long) latencyMs);
        resumeLatencySamples.increment();
        
        // Record in histogram
        for (int i = 0; i < BUCKET_BOUNDS.length; i++) {
            if (latencyMs < BUCKET_BOUNDS[i]) {
                latencyBuckets[i].increment();
                break;
            }
        }
    }
    
    public void report() {
        long identifies = identifyCount.sum();
        long resumeSuccess = resumeSuccessCount.sum();
        long resumeFailed = resumeFailedCount.sum();
        long totalResumes = resumeSuccess + resumeFailed;
        
        double successRate = totalResumes > 0 ? 
            (resumeSuccess * 100.0 / totalResumes) : 0.0;
        
        long samples = resumeLatencySamples.sum();
        double avgLatency = samples > 0 ? 
            (totalResumeLatencyMs.get() / (double) samples) : 0.0;
        
        System.out.println("\n=== Gateway Metrics ===");
        System.out.println("Identifies: " + identifies);
        System.out.println("Resume Success: " + resumeSuccess);
        System.out.println("Resume Failed: " + resumeFailed);
        System.out.printf("Resume Success Rate: %.2f%%\n", successRate);
        System.out.printf("Avg Resume Latency: %.2f ms\n", avgLatency);
        System.out.println();
    }
    
    public double getResumeSuccessRate() {
        long success = resumeSuccessCount.sum();
        long failed = resumeFailedCount.sum();
        long total = success + failed;
        return total > 0 ? (success * 100.0 / total) : 0.0;
    }
    
    public double getAverageResumeLatency() {
        long samples = resumeLatencySamples.sum();
        return samples > 0 ? (totalResumeLatencyMs.get() / (double) samples) : 0.0;
    }
    
    public long[] getLatencyHistogram() {
        long[] histogram = new long[latencyBuckets.length];
        for (int i = 0; i < latencyBuckets.length; i++) {
            histogram[i] = latencyBuckets[i].sum();
        }
        return histogram;
    }
    
    // Generate sample metrics for demonstration
    public void generateSampleData() {
        // Generate some identify events
        for (int i = 0; i < 50; i++) {
            identifyCount.increment();
        }
        
        // Generate resume attempts: 95% success rate
        int successCount = 95;
        int failedCount = 5;
        
        for (int i = 0; i < successCount; i++) {
            resumeSuccessCount.increment();
            // Record various latencies with realistic distribution
            double[] sampleLatencies = {2.5, 5.2, 8.1, 12.3, 15.7, 18.9, 22.4, 35.6, 48.2, 
                                       65.3, 82.1, 95.4, 125.7, 180.3, 250.8, 380.5, 520.1, 
                                       750.3, 1200.5, 2500.8};
            recordResumeLatency(sampleLatencies[i % sampleLatencies.length]);
        }
        
        for (int i = 0; i < failedCount; i++) {
            resumeFailedCount.increment();
        }
    }
}
