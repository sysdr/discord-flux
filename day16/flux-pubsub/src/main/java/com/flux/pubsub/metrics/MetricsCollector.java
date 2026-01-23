package com.flux.pubsub.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    private final LongAdder publishCount = new LongAdder();
    private final LongAdder publishErrors = new LongAdder();
    private final LongAdder consumeCount = new LongAdder();
    private final LongAdder consumeErrors = new LongAdder();
    private final LongAdder batchSizeSum = new LongAdder();
    private final LongAdder batchCount = new LongAdder();
    private final AtomicLong publishLatencySum = new AtomicLong(0);
    private final AtomicLong consumeLatencySum = new AtomicLong(0);
    
    private final ConcurrentHashMap<Long, LongAdder> fanOutCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, LongAdder> droppedCounts = new ConcurrentHashMap<>();

    public void recordPublish(long latencyNanos) {
        publishCount.increment();
        publishLatencySum.addAndGet(latencyNanos);
    }

    public void recordPublishError() {
        publishErrors.increment();
    }

    public void recordConsume(long latencyNanos) {
        consumeCount.increment();
        consumeLatencySum.addAndGet(latencyNanos);
    }

    public void recordConsumeError() {
        consumeErrors.increment();
    }

    public void recordBatchSize(int size) {
        batchSizeSum.add(size);
        batchCount.increment();
    }

    public void recordFanOut(long guildId, int count) {
        fanOutCounts.computeIfAbsent(guildId, k -> new LongAdder()).add(count);
    }

    public void recordDropped(long guildId, long count) {
        droppedCounts.computeIfAbsent(guildId, k -> new LongAdder()).add(count);
    }

    // Snapshot for dashboard
    public MetricsSnapshot snapshot() {
        long publishTotal = publishCount.sum();
        long consumeTotal = consumeCount.sum();
        
        return new MetricsSnapshot(
            publishTotal,
            publishErrors.sum(),
            publishTotal > 0 ? publishLatencySum.get() / publishTotal : 0,
            consumeTotal,
            consumeErrors.sum(),
            consumeTotal > 0 ? consumeLatencySum.get() / consumeTotal : 0,
            batchCount.sum() > 0 ? (double) batchSizeSum.sum() / batchCount.sum() : 0,
            fanOutCounts.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    java.util.Map.Entry::getKey,
                    e -> e.getValue().sum()
                )),
            droppedCounts.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    java.util.Map.Entry::getKey,
                    e -> e.getValue().sum()
                ))
        );
    }

    public void reset() {
        publishCount.reset();
        publishErrors.reset();
        consumeCount.reset();
        consumeErrors.reset();
        batchSizeSum.reset();
        batchCount.reset();
        publishLatencySum.set(0);
        consumeLatencySum.set(0);
        fanOutCounts.clear();
        droppedCounts.clear();
    }

    public record MetricsSnapshot(
        long publishCount,
        long publishErrors,
        long avgPublishLatencyNanos,
        long consumeCount,
        long consumeErrors,
        long avgConsumeLatencyNanos,
        double avgBatchSize,
        java.util.Map<Long, Long> fanOutByGuild,
        java.util.Map<Long, Long> droppedByGuild
    ) {
        public String toJson() {
            return """
                {
                    "publishCount": %d,
                    "publishErrors": %d,
                    "avgPublishLatencyMs": %.2f,
                    "consumeCount": %d,
                    "consumeErrors": %d,
                    "avgConsumeLatencyMs": %.2f,
                    "avgBatchSize": %.1f,
                    "fanOutByGuild": %s,
                    "droppedByGuild": %s
                }
                """.formatted(
                publishCount,
                publishErrors,
                avgPublishLatencyNanos / 1_000_000.0,
                consumeCount,
                consumeErrors,
                avgConsumeLatencyNanos / 1_000_000.0,
                avgBatchSize,
                mapToJson(fanOutByGuild),
                mapToJson(droppedByGuild)
            );
        }

        private String mapToJson(java.util.Map<Long, Long> map) {
            if (map.isEmpty()) return "{}";
            return "{" + map.entrySet().stream()
                .map(e -> "\"%d\": %d".formatted(e.getKey(), e.getValue()))
                .collect(java.util.stream.Collectors.joining(", ")) + "}";
        }
    }
}
