package com.flux.loadtest;

import com.flux.snowflake.SnowflakeGenerator;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-throughput load test for Snowflake generator.
 */
public class LoadTestRunner {
    
    public static void main(String[] args) throws InterruptedException {
        int threadCount = 1000;
        int idsPerThread = 10000;
        long workerId = 1;
        
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║     SNOWFLAKE ID GENERATOR - LOAD TEST            ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.printf("Configuration:%n");
        System.out.printf("  Virtual Threads: %,d%n", threadCount);
        System.out.printf("  IDs per Thread:  %,d%n", idsPerThread);
        System.out.printf("  Total IDs:       %,d%n", threadCount * idsPerThread);
        System.out.printf("  Worker ID:       %d%n", workerId);
        System.out.println();
        
        SnowflakeGenerator generator = new SnowflakeGenerator(workerId);
        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong duplicates = new AtomicLong(0);
        AtomicLong totalLatencyNs = new AtomicLong(0);
        
        System.out.println("🚀 Starting load test...");
        long startTime = System.nanoTime();
        
        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                long threadLatency = 0;
                
                for (int j = 0; j < idsPerThread; j++) {
                    long start = System.nanoTime();
                    long id = generator.nextId();
                    threadLatency += System.nanoTime() - start;
                    
                    if (!allIds.add(id)) {
                        duplicates.incrementAndGet();
                    }
                }
                
                totalLatencyNs.addAndGet(threadLatency);
                latch.countDown();
            });
        }
        
        latch.await();
        long elapsedNs = System.nanoTime() - startTime;
        
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║                   RESULTS                          ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        
        int totalIds = threadCount * idsPerThread;
        double elapsedSec = elapsedNs / 1_000_000_000.0;
        double throughput = totalIds / elapsedSec;
        double avgLatencyNs = (double) totalLatencyNs.get() / totalIds;
        
        System.out.printf("  ✅ Total IDs Generated:    %,d%n", totalIds);
        System.out.printf("  ✅ Unique IDs:             %,d%n", allIds.size());
        System.out.printf("  ❌ Duplicates Found:       %,d%n", duplicates.get());
        System.out.printf("  ⏱️  Total Time:            %.2f seconds%n", elapsedSec);
        System.out.printf("  🚀 Throughput:             %,.0f IDs/sec%n", throughput);
        System.out.printf("  ⚡ Avg Latency:            %.0f ns%n", avgLatencyNs);
        System.out.printf("  📊 Clock Drift Events:     %,d%n", generator.getClockDriftEvents());
        System.out.printf("  ⚠️  Sequence Exhaustion:   %,d%n", generator.getSequenceExhaustionEvents());
        System.out.println();
        
        if (duplicates.get() == 0) {
            System.out.println("✅ SUCCESS: All IDs are unique!");
        } else {
            System.out.println("❌ FAILURE: Duplicates detected!");
        }
        
        System.out.println();
    }
}
