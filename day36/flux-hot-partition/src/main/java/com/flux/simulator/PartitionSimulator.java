package com.flux.simulator;

import com.flux.generator.SnowflakeGenerator;
import com.flux.partition.BucketStrategy;
import com.flux.partition.Message;
import com.flux.partition.PartitionKey;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Simulates write workloads to demonstrate partition distribution.
 */
public class PartitionSimulator {
    private final SnowflakeGenerator generator;
    private final ConcurrentHashMap<PartitionKey, PartitionStats> partitionMap;

    public PartitionSimulator(long workerId) {
        this.generator = new SnowflakeGenerator(workerId);
        this.partitionMap = new ConcurrentHashMap<>();
    }

    /**
     * Simulate writes to a channel using the specified bucketing strategy.
     */
    public SimulationResult simulateWrites(
            long channelId,
            int messagesPerSecond,
            Duration duration,
            BucketStrategy strategy
    ) {
        long totalMessages = messagesPerSecond * duration.toSeconds();
        long startTime = System.nanoTime();
        CountDownLatch latch = new CountDownLatch((int) totalMessages);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < totalMessages; i++) {
                final int authorId = ThreadLocalRandom.current().nextInt(1000, 10000);
                executor.submit(() -> {
                    try {
                        long snowflakeId = generator.nextId();
                        Message message = new Message(
                                snowflakeId,
                                channelId,
                                authorId,
                                "Message content " + snowflakeId,
                                System.currentTimeMillis()
                        );

                        PartitionKey key = PartitionKey.fromMessage(channelId, snowflakeId, strategy);
                        partitionMap.computeIfAbsent(key, k -> new PartitionStats())
                                .recordWrite(message);
                    } finally {
                        latch.countDown();
                    }
                });

                // Throttle to maintain target rate
                if (i % messagesPerSecond == 0 && i > 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // Wait for all writes to complete
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simulation interrupted", e);
        }

        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;

        return new SimulationResult(
                strategy,
                totalMessages,
                partitionMap.size(),
                durationSeconds,
                getPartitionStats()
        );
    }

    /**
     * Get current partition statistics.
     */
    public Map<PartitionKey, PartitionStats> getPartitionStats() {
        return Map.copyOf(partitionMap);
    }

    /**
     * Reset simulator state.
     */
    public void reset() {
        partitionMap.clear();
    }

    /**
     * Statistics for a single partition.
     */
    public static class PartitionStats {
        private final AtomicLong messageCount = new AtomicLong(0);
        private final AtomicLong totalBytes = new AtomicLong(0);

        public void recordWrite(Message message) {
            messageCount.incrementAndGet();
            totalBytes.addAndGet(message.estimatedBytes());
        }

        public long getMessageCount() {
            return messageCount.get();
        }

        public long getTotalBytes() {
            return totalBytes.get();
        }

        public double getSizeMB() {
            return totalBytes.get() / (1024.0 * 1024.0);
        }
    }

    /**
     * Result of a simulation run.
     */
    public record SimulationResult(
            BucketStrategy strategy,
            long totalMessages,
            int partitionCount,
            double durationSeconds,
            Map<PartitionKey, PartitionStats> partitionStats
    ) {
        public double throughputPerSecond() {
            return totalMessages / durationSeconds;
        }

        public long maxPartitionSize() {
            return partitionStats.values().stream()
                    .mapToLong(PartitionStats::getMessageCount)
                    .max()
                    .orElse(0);
        }

        public double avgPartitionSize() {
            return partitionStats.values().stream()
                    .mapToLong(PartitionStats::getMessageCount)
                    .average()
                    .orElse(0);
        }

        public String summary() {
            return """
                    Simulation Results:
                    ------------------
                    Strategy: %s
                    Total Messages: %,d
                    Partitions Created: %d
                    Duration: %.2f seconds
                    Throughput: %,.0f msgs/sec
                    Max Partition Size: %,d messages
                    Avg Partition Size: %.0f messages
                    """.formatted(
                    strategy,
                    totalMessages,
                    partitionCount,
                    durationSeconds,
                    throughputPerSecond(),
                    maxPartitionSize(),
                    avgPartitionSize()
            );
        }
    }
}
