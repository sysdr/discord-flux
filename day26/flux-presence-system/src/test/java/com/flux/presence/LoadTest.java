package com.flux.presence;

import com.flux.presence.core.PresenceService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Load test to measure presence system throughput and latency.
 */
public class LoadTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Flux Presence System Load Test ===\n");
        
        PresenceService service = new PresenceService("localhost", 6379);
        
        // Test 1: Concurrent writes
        testConcurrentWrites(service, 10_000);
        
        // Test 2: Cache hit rate
        testCacheHitRate(service, 5_000);
        
        // Test 3: Thundering herd
        testThunderingHerd(service, 5_000);
        
        service.close();
        System.out.println("\n=== Load Test Complete ===");
    }
    
    private static void testConcurrentWrites(PresenceService service, int count) throws Exception {
        System.out.println("Test 1: Concurrent Writes (" + count + " users)");
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(count);
        
        long start = System.nanoTime();
        
        for (int i = 0; i < count; i++) {
            long userId = 100_000L + i;
            executor.submit(() -> {
                service.markOnline(userId);
                latch.countDown();
            });
        }
        
        latch.await();
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        
        System.out.println("  Queued " + count + " writes in " + duration + "ms");
        System.out.println("  Throughput: " + (count * 1000L / duration) + " ops/sec\n");
        
        // Wait for batch flush
        Thread.sleep(2000);
        
        var metrics = service.getMetrics();
        System.out.println("  Metrics after flush:");
        System.out.println("    Redis writes: " + metrics.redisWrites());
        System.out.println("    Pending queue: " + metrics.pendingQueueSize());
        System.out.println();
        
        executor.shutdown();
    }
    
    private static void testCacheHitRate(PresenceService service, int queries) throws Exception {
        System.out.println("Test 2: Cache Hit Rate (" + queries + " queries on 100 users)");
        
        // Populate presence for 100 users
        for (int i = 0; i < 100; i++) {
            service.markOnline(200_000L + i);
        }
        Thread.sleep(1000); // Wait for flush
        
        // Query randomly
        var metricsBefore = service.getMetrics();
        
        for (int i = 0; i < queries; i++) {
            long userId = 200_000L + (long)(Math.random() * 100);
            service.getPresence(userId).join();
        }
        
        var metricsAfter = service.getMetrics();
        double hitRate = metricsAfter.cacheHitRate();
        
        System.out.println("  Cache hits: " + (metricsAfter.cacheHits() - metricsBefore.cacheHits()));
        System.out.println("  Cache misses: " + (metricsAfter.cacheMisses() - metricsBefore.cacheMisses()));
        System.out.println("  Hit rate: " + String.format("%.2f%%", hitRate));
        System.out.println();
    }
    
    private static void testThunderingHerd(PresenceService service, int count) throws Exception {
        System.out.println("Test 3: Thundering Herd (" + count + " simultaneous connects)");
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(count);
        
        long start = System.nanoTime();
        
        for (int i = 0; i < count; i++) {
            long userId = 300_000L + i;
            executor.submit(() -> {
                service.markOnline(userId);
                latch.countDown();
            });
        }
        
        latch.await();
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        
        System.out.println("  All users connected in " + duration + "ms");
        System.out.println("  Average: " + (duration * 1000 / count) + " µs per user");
        
        Thread.sleep(2000); // Wait for batch flush
        
        var metrics = service.getMetrics();
        System.out.println("  Pending queue size: " + metrics.pendingQueueSize());
        System.out.println();
        
        executor.shutdown();
    }
}
