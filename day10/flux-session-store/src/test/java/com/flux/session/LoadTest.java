package com.flux.session;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load test to simulate production-scale session creation/access patterns.
 * Run with: mvn test -Dtest=LoadTest
 */
public class LoadTest {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Flux Session Store Load Test ===\n");

        // Test parameters
        int totalSessions = 100_000;
        int concurrentThreads = 1000;
        int operationsPerThread = totalSessions / concurrentThreads;

        // Initialize store
        ProductionSessionStore store = new ProductionSessionStore(totalSessions, 300);
        AtomicLong sessionIdGen = new AtomicLong(1);

        System.out.println("Configuration:");
        System.out.println("  Total Sessions: " + totalSessions);
        System.out.println("  Concurrent Threads: " + concurrentThreads);
        System.out.println("  Operations/Thread: " + operationsPerThread);
        System.out.println();

        // Warmup
        System.out.println("Warmup phase...");
        for (int i = 0; i < 1000; i++) {
            Session s = createSession(sessionIdGen.getAndIncrement());
            store.addSession(s);
            store.getSession(s.sessionId());
        }
        store.clear();
        System.gc();
        Thread.sleep(2000);

        // Test 1: Creation throughput
        System.out.println("Test 1: Session Creation Throughput");
        long createStart = System.nanoTime();
        CountDownLatch createLatch = new CountDownLatch(concurrentThreads);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < concurrentThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        Session session = createSession(sessionIdGen.getAndIncrement());
                        store.addSession(session);
                    }
                } finally {
                    createLatch.countDown();
                }
            });
        }

        createLatch.await();
        long createEnd = System.nanoTime();
        double createTimeMs = (createEnd - createStart) / 1_000_000.0;
        double createThroughput = totalSessions / (createTimeMs / 1000.0);

        System.out.println("  Time: " + String.format("%.2f", createTimeMs) + " ms");
        System.out.println("  Throughput: " + String.format("%.0f", createThroughput) + " creates/sec");
        System.out.println("  Actual size: " + store.size());
        System.out.println();

        // Test 2: Read throughput
        System.out.println("Test 2: Session Read Throughput");
        long readStart = System.nanoTime();
        CountDownLatch readLatch = new CountDownLatch(concurrentThreads);

        for (int i = 0; i < concurrentThreads; i++) {
            final int startId = i * operationsPerThread;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        store.getSession(startId + j + 1);
                    }
                } finally {
                    readLatch.countDown();
                }
            });
        }

        readLatch.await();
        long readEnd = System.nanoTime();
        double readTimeMs = (readEnd - readStart) / 1_000_000.0;
        double readThroughput = totalSessions / (readTimeMs / 1000.0);

        System.out.println("  Time: " + String.format("%.2f", readTimeMs) + " ms");
        System.out.println("  Throughput: " + String.format("%.0f", readThroughput) + " reads/sec");
        System.out.println();

        // Test 3: Mixed workload
        System.out.println("Test 3: Mixed Workload (50% read, 50% update)");
        long mixedStart = System.nanoTime();
        CountDownLatch mixedLatch = new CountDownLatch(concurrentThreads);

        for (int i = 0; i < concurrentThreads; i++) {
            final int startId = i * operationsPerThread;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        long sessionId = startId + j + 1;
                        if (j % 2 == 0) {
                            store.getSession(sessionId);
                        } else {
                            store.getSession(sessionId).ifPresent(session -> {
                                Session updated = session.updateActivity();
                                store.updateSession(updated);
                            });
                        }
                    }
                } finally {
                    mixedLatch.countDown();
                }
            });
        }

        mixedLatch.await();
        long mixedEnd = System.nanoTime();
        double mixedTimeMs = (mixedEnd - mixedStart) / 1_000_000.0;
        double mixedThroughput = totalSessions / (mixedTimeMs / 1000.0);

        System.out.println("  Time: " + String.format("%.2f", mixedTimeMs) + " ms");
        System.out.println("  Throughput: " + String.format("%.0f", mixedThroughput) + " ops/sec");
        System.out.println();

        // Memory stats
        SessionMetrics metrics = store.getMetrics();
        System.out.println("Final Metrics:");
        System.out.println("  Total Sessions: " + metrics.totalSessions());
        System.out.println("  Heap Used: " + metrics.heapUsedMB() + " MB");
        System.out.println("  Bytes/Session: " + (metrics.heapUsedMB() * 1024 * 1024 / metrics.totalSessions()));

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        store.shutdown();

        System.out.println("\n=== Load Test Complete ===");
    }

    private static Session createSession(long id) {
        return new Session(
            id,
            1000 + (id % 10000),
            new InetSocketAddress("127.0.0.1", 50000 + (int)(id % 10000)),
            Instant.now(),
            Instant.now(),
            SessionState.ACTIVE
        );
    }
}
