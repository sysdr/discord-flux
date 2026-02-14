package com.flux.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load test to measure routing throughput and latency.
 */
public class LoadTest {
    
    public static void main(String[] args) throws InterruptedException {
        int totalRequests = args.length > 0 ? Integer.parseInt(args[0]) : 1_000_000;
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        
        ConsistentHashRing<GatewayNode> ring = new ConsistentHashRing<>(150);
        
        // Add nodes
        for (int i = 1; i <= 3; i++) {
            ring.addNode(new GatewayNode(
                "node-" + i,
                "10.0.0." + i,
                9000 + i
            ));
        }
        
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Load Test Configuration");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Total Requests: " + String.format("%,d", totalRequests));
        System.out.println("Virtual Threads: " + threads);
        System.out.println("Nodes: " + ring.getNodes().size());
        System.out.println("Virtual Nodes: " + ring.size());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicLong counter = new AtomicLong(0);
        List<Long> latencies = new ArrayList<>();
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            Thread.ofVirtual().start(() -> {
                int requestsPerThread = totalRequests / threads;
                for (int j = 0; j < requestsPerThread; j++) {
                    String key = "user_" + threadId + "_" + j;
                    
                    long start = System.nanoTime();
                    ring.get(key);
                    long end = System.nanoTime();
                    
                    synchronized (latencies) {
                        latencies.add(end - start);
                    }
                    
                    counter.incrementAndGet();
                }
                latch.countDown();
            });
        }
        
        latch.await();
        long endTime = System.nanoTime();
        
        double durationSec = (endTime - startTime) / 1_000_000_000.0;
        double throughput = totalRequests / durationSec;
        
        latencies.sort(Long::compare);
        long p50 = latencies.get(latencies.size() / 2);
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        long p999 = latencies.get((int) (latencies.size() * 0.999));
        long avg = latencies.stream().mapToLong(Long::longValue).sum() / latencies.size();
        
        System.out.println("Results:");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println(String.format("Duration:    %.2f seconds", durationSec));
        System.out.println(String.format("Throughput:  %,.0f requests/sec", throughput));
        System.out.println(String.format("Avg Latency: %d ns", avg));
        System.out.println(String.format("p50 Latency: %d ns", p50));
        System.out.println(String.format("p99 Latency: %d ns", p99));
        System.out.println(String.format("p999 Latency:%d ns", p999));
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        if (p99 < 100) {
            System.out.println("✓ EXCELLENT: p99 < 100ns (lock-free reads working)");
        } else if (p99 < 500) {
            System.out.println("✓ GOOD: p99 < 500ns (acceptable performance)");
        } else {
            System.out.println("⚠ WARNING: p99 > 500ns (possible lock contention)");
        }
    }
}
