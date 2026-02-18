package com.flux.shard.gateway;

import com.flux.shard.router.ShardRouter;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

/**
 * Tracks event distribution across shards using striped counters
 * to minimize cache line contention.
 */
public class ShardDistributionTracker {
    
    private static final int NUM_STRIPES = 16;
    
    private final int totalShards;
    private final long[][] stripedCounters;
    private final StampedLock lock = new StampedLock();
    
    public ShardDistributionTracker(int totalShards) {
        this.totalShards = totalShards;
        this.stripedCounters = new long[NUM_STRIPES][totalShards];
    }
    
    /**
     * Record an event for a guild (lock-free for individual threads)
     */
    public void recordEvent(long guildId) {
        int shard = ShardRouter.calculateShard(guildId, totalShards);
        int stripe = (int) (Thread.currentThread().threadId() % NUM_STRIPES);
        stripedCounters[stripe][shard]++;
    }
    
    /**
     * Get aggregated counts across all stripes
     */
    public long[] getShardCounts() {
        long stamp = lock.readLock();
        try {
            long[] totals = new long[totalShards];
            for (int stripe = 0; stripe < NUM_STRIPES; stripe++) {
                for (int shard = 0; shard < totalShards; shard++) {
                    totals[shard] += stripedCounters[stripe][shard];
                }
            }
            return totals;
        } finally {
            lock.unlockRead(stamp);
        }
    }
    
    /**
     * Calculate distribution statistics
     */
    public DistributionStats getStats() {
        long[] counts = getShardCounts();
        long total = Arrays.stream(counts).sum();
        if (total == 0) {
            return new DistributionStats(0, 0, 0, 0);
        }
        
        double mean = total / (double) totalShards;
        
        double variance = Arrays.stream(counts)
            .mapToDouble(count -> Math.pow(count - mean, 2))
            .average()
            .orElse(0.0);
        
        long max = Arrays.stream(counts).max().orElse(0);
        long min = Arrays.stream(counts).min().orElse(0);
        
        return new DistributionStats(mean, Math.sqrt(variance), min, max);
    }
    
    /**
     * Reset all counters
     */
    public void reset() {
        long stamp = lock.writeLock();
        try {
            for (int stripe = 0; stripe < NUM_STRIPES; stripe++) {
                Arrays.fill(stripedCounters[stripe], 0);
            }
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    public record DistributionStats(
        double mean,
        double stdDev,
        long min,
        long max
    ) {
        public double coefficientOfVariation() {
            return mean > 0 ? (stdDev / mean) * 100.0 : 0.0;
        }
        
        public double maxDeviation() {
            return mean > 0 ? max / mean : 0.0;
        }
    }
}
