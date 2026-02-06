package com.flux.persistence;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector {
    private final ConcurrentLinkedQueue<Long> oneLatencies = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> quorumLatencies = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalWrites = new AtomicLong(0);
    private final AtomicLong failedWrites = new AtomicLong(0);
    private final AtomicLong staleReads = new AtomicLong(0);
    
    public void recordWrite(ConsistencyLevel level, WriteResult result) {
        totalWrites.incrementAndGet();
        
        if (!result.success()) {
            failedWrites.incrementAndGet();
            return;
        }
        
        if (level == ConsistencyLevel.ONE) {
            oneLatencies.add(result.latencyMs());
        } else if (level == ConsistencyLevel.QUORUM) {
            quorumLatencies.add(result.latencyMs());
        }
        
        // Keep only last 1000 samples to avoid memory bloat
        if (oneLatencies.size() > 1000) oneLatencies.poll();
        if (quorumLatencies.size() > 1000) quorumLatencies.poll();
    }
    
    public void recordStaleRead() {
        staleReads.incrementAndGet();
    }
    
    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
            calculateStats(oneLatencies),
            calculateStats(quorumLatencies),
            totalWrites.get(),
            failedWrites.get(),
            staleReads.get()
        );
    }
    
    private LatencyStats calculateStats(ConcurrentLinkedQueue<Long> latencies) {
        if (latencies.isEmpty()) {
            return new LatencyStats(0, 0, 0, 0);
        }
        
        List<Long> sorted = new ArrayList<>(latencies);
        sorted.sort(Long::compareTo);
        
        DoubleSummaryStatistics stats = sorted.stream()
            .mapToDouble(Long::doubleValue)
            .summaryStatistics();
        
        int p50Index = sorted.size() / 2;
        int p99Index = (int) (sorted.size() * 0.99);
        
        return new LatencyStats(
            stats.getAverage(),
            sorted.get(p50Index),
            sorted.get(Math.min(p99Index, sorted.size() - 1)),
            stats.getMax()
        );
    }
    
    public record LatencyStats(double avg, long p50, long p99, double max) {}
    
    public record MetricsSnapshot(
        LatencyStats oneStats,
        LatencyStats quorumStats,
        long totalWrites,
        long failedWrites,
        long staleReads
    ) {}
}
