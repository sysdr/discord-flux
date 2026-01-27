package com.flux.publisher.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free metrics collector using LongAdder.
 * LongAdder outperforms AtomicLong under high contention (100K+ ops/sec).
 * 
 * Theory: LongAdder uses striping to reduce CAS contention.
 * Multiple threads update different cells, sum() aggregates lazily.
 */
public class MetricsCollector {
    
    // Global metrics
    private final LongAdder totalPublished = new LongAdder();
    private final LongAdder totalErrors = new LongAdder();
    private final LongAdder totalRateLimited = new LongAdder();
    private final LongAdder totalLatencyNanos = new LongAdder();
    
    // Per-guild metrics
    private final Map<String, GuildMetrics> guildMetrics = new ConcurrentHashMap<>();

    public void recordPublishSuccess(String guildId, long latencyNanos) {
        totalPublished.increment();
        totalLatencyNanos.add(latencyNanos);
        
        guildMetrics.computeIfAbsent(guildId, k -> new GuildMetrics())
            .recordSuccess(latencyNanos);
    }

    public void recordPublishError(String guildId) {
        totalErrors.increment();
        guildMetrics.computeIfAbsent(guildId, k -> new GuildMetrics())
            .recordError();
    }

    public void recordRateLimited() {
        totalRateLimited.increment();
    }

    public MetricsSnapshot getSnapshot() {
        long published = totalPublished.sum();
        long errors = totalErrors.sum();
        long rateLimited = totalRateLimited.sum();
        long totalLatency = totalLatencyNanos.sum();
        
        long avgLatencyNanos = published > 0 ? totalLatency / published : 0;
        
        return new MetricsSnapshot(
            published,
            errors,
            rateLimited,
            avgLatencyNanos,
            guildMetrics.size()
        );
    }

    public Map<String, GuildMetrics> getGuildMetrics() {
        return Map.copyOf(guildMetrics);
    }

    public void reset() {
        totalPublished.reset();
        totalErrors.reset();
        totalRateLimited.reset();
        totalLatencyNanos.reset();
        guildMetrics.clear();
    }

    public static class GuildMetrics {
        private final LongAdder published = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder totalLatency = new LongAdder();

        void recordSuccess(long latencyNanos) {
            published.increment();
            totalLatency.add(latencyNanos);
        }

        void recordError() {
            errors.increment();
        }

        public long getPublished() { return published.sum(); }
        public long getErrors() { return errors.sum(); }
        public long getAvgLatencyNanos() {
            long p = published.sum();
            return p > 0 ? totalLatency.sum() / p : 0;
        }
    }

    public record MetricsSnapshot(
        long totalPublished,
        long totalErrors,
        long totalRateLimited,
        long avgLatencyNanos,
        int activeGuilds
    ) {}
}
