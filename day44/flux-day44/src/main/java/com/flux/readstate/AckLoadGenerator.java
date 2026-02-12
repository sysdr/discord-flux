package com.flux.readstate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Load generator for the Ack Tracker.
 *
 * Spawns N Virtual Threads, each firing acks continuously.
 * Uses Virtual Threads to spawn 500 concurrent "users" without
 * touching the OS thread pool — demonstrating why Virtual Threads
 * are the right primitive for IO-bound concurrent workloads.
 *
 * Run with: java -cp target/... com.flux.readstate.AckLoadGenerator
 */
public final class AckLoadGenerator {

    private final AckTracker          ackTracker;
    private final SnowflakeIdGenerator snowflake;
    private final int                 concurrentUsers;
    private final int                 durationSeconds;
    private final AtomicBoolean       running = new AtomicBoolean(true);
    private final LongAdder           acks    = new LongAdder();

    public AckLoadGenerator(AckTracker ackTracker, SnowflakeIdGenerator snowflake,
                            int concurrentUsers, int durationSeconds) {
        this.ackTracker      = ackTracker;
        this.snowflake       = snowflake;
        this.concurrentUsers = concurrentUsers;
        this.durationSeconds = durationSeconds;
    }

    public LoadResult run() throws InterruptedException {
        System.out.printf("%n=== Ack Load Generator: %d virtual users, %ds ===%n",
            concurrentUsers, durationSeconds);

        var latch    = new CountDownLatch(concurrentUsers);
        var threads  = new ArrayList<Thread>(concurrentUsers);

        long startNs = System.nanoTime();

        for (int i = 0; i < concurrentUsers; i++) {
            final int userId = (i % ChannelSimulator.USER_COUNT) + 1;
            var t = Thread.ofVirtual().name("load-user-" + i).start(() -> {
                var rng = ThreadLocalRandom.current();
                try {
                    while (running.get()) {
                        long channelId = rng.nextLong(1, ChannelSimulator.CHANNEL_COUNT + 1);
                        long messageId = snowflake.nextId();
                        ackTracker.onNewMessage(channelId, messageId);
                        ackTracker.ack(new AckCommand(userId, channelId, messageId, 0));
                        acks.increment();
                        // Simulate human think time: 5-50ms between acks
                        Thread.sleep(rng.nextLong(5, 50));
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    latch.countDown();
                }
            });
            threads.add(t);
        }

        // Run for specified duration
        Thread.sleep(durationSeconds * 1000L);
        running.set(false);
        threads.forEach(Thread::interrupt);
        latch.await(5, TimeUnit.SECONDS);

        long elapsedNs   = System.nanoTime() - startNs;
        double elapsedS  = elapsedNs / 1e9;
        long   totalAcks = acks.sum();
        var    metrics   = ackTracker.getMetrics();

        return new LoadResult(
            totalAcks,
            (long)(totalAcks / elapsedS),
            metrics.cassandraWrites(),
            metrics.coalescingRatio(),
            metrics.staleAcks(),
            metrics.totalEntries()
        );
    }

    public record LoadResult(
        long   totalAcks,
        long   ackRatePerSec,
        long   cassandraWrites,
        double coalescingRatio,
        long   staleAcks,
        int    totalEntries
    ) {
        public void print() {
            System.out.println("\n=== Load Test Results ===");
            System.out.printf("  Total Acks Fired   : %,d%n", totalAcks);
            System.out.printf("  Ack Rate           : %,d acks/sec%n", ackRatePerSec);
            System.out.printf("  Cassandra Writes   : %,d%n", cassandraWrites);
            System.out.printf("  Coalescing Ratio   : %.1f:1  (%.1f%% write reduction)%n",
                coalescingRatio, coalescingRatio > 0 ? (1 - 1.0/coalescingRatio)*100 : 0);
            System.out.printf("  Stale Acks Dropped : %,d%n", staleAcks);
            System.out.printf("  Total State Entries: %,d%n", totalEntries);
            System.out.println("=========================");
        }
    }
}
