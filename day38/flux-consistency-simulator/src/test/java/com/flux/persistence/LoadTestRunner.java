package com.flux.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadTestRunner {
    public static void main(String[] args) {
        System.out.println("🔥 Starting Load Test: 1000 concurrent writes");
        
        List<ReplicaNode> replicas = List.of(
            new ReplicaNode(1),
            new ReplicaNode(2),
            new ReplicaNode(3)
        );
        
        CoordinatorNode coordinator = new CoordinatorNode(replicas);
        MetricsCollector metrics = new MetricsCollector();
        SnowflakeGenerator idGen = new SnowflakeGenerator(1);
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 1000; i++) {
            final int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                ConsistencyLevel level = index % 2 == 0 ? ConsistencyLevel.ONE : ConsistencyLevel.QUORUM;
                Message msg = Message.create("channel-1", "user-" + index, "Load test message", idGen);
                WriteResult result = coordinator.write(msg, level);
                metrics.recordWrite(level, result);
                
                if (index % 100 == 0) {
                    System.out.println("Progress: " + index + "/1000");
                }
            }, executor);
            
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long elapsed = System.currentTimeMillis() - startTime;
        MetricsCollector.MetricsSnapshot snapshot = metrics.getSnapshot();
        
        System.out.println("\n✅ Load Test Complete");
        System.out.println("Total Time: " + elapsed + "ms");
        System.out.println("Throughput: " + (1000.0 / elapsed * 1000) + " writes/sec");
        System.out.println("\nONE Latency:");
        System.out.println("  Avg: " + snapshot.oneStats().avg() + "ms");
        System.out.println("  P99: " + snapshot.oneStats().p99() + "ms");
        System.out.println("\nQUORUM Latency:");
        System.out.println("  Avg: " + snapshot.quorumStats().avg() + "ms");
        System.out.println("  P99: " + snapshot.quorumStats().p99() + "ms");
        System.out.println("\nFailed: " + snapshot.failedWrites() + "/" + snapshot.totalWrites());
        
        executor.shutdown();
        replicas.forEach(ReplicaNode::shutdown);
    }
}
