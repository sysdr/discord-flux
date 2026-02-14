package com.flux.rebalancing;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles graceful connection migration using Virtual Threads.
 * Spawns lightweight threads per connection without kernel thread exhaustion.
 */
public class ConnectionMigrator {
    
    private final ConsistentHashRing ring;
    private final Map<String, ConnectionMetrics> migrationMetrics;
    
    public ConnectionMigrator(ConsistentHashRing ring) {
        this.ring = ring;
        this.migrationMetrics = new ConcurrentHashMap<>();
    }
    
    public record ConnectionMetrics(
        String connectionId,
        Instant startTime,
        Instant completionTime,
        boolean successful,
        String failureReason
    ) {}
    
    /**
     * Calculate which connections need to move when a node is added.
     * Uses two-phase simulation to determine impact before committing.
     */
    public List<MigrationPlan> planNodeAddition(
        GatewayNode newNode,
        Map<String, GatewayNode> currentConnectionMap
    ) {
        var affectedConnections = new HashMap<GatewayNode, Set<String>>();
        
        for (var entry : currentConnectionMap.entrySet()) {
            String connectionId = entry.getKey();
            GatewayNode currentNode = entry.getValue();
            
            // Simulate: where would this go after adding newNode?
            ring.addNode(newNode);
            GatewayNode futureNode = ring.getNodeForConnection(connectionId);
            ring.removeNode(newNode.nodeId());
            
            // If assignment changes, migration needed
            if (!currentNode.nodeId().equals(futureNode.nodeId())) {
                affectedConnections
                    .computeIfAbsent(futureNode, k -> ConcurrentHashMap.newKeySet())
                    .add(connectionId);
            }
        }
        
        // Create migration plans
        List<MigrationPlan> plans = new ArrayList<>();
        for (var entry : affectedConnections.entrySet()) {
            plans.add(MigrationPlan.create(entry.getKey(), entry.getValue()));
        }
        
        return plans;
    }
    
    /**
     * Execute migration using Virtual Threads (Java 21+).
     * Each connection gets its own lightweight thread for blocking I/O.
     */
    public CompletableFuture<MigrationResult> executeMigration(
        MigrationPlan plan,
        Map<String, GatewayNode> connectionMap
    ) {
        return CompletableFuture.supplyAsync(() -> {
            var result = new MigrationResult();
            result.startTime = Instant.now();
            
            AtomicInteger completed = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            
            // Virtual Thread executor - creates lightweight threads
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                
                List<CompletableFuture<Void>> migrations = new ArrayList<>();
                
                for (String connectionId : plan.connectionsToMove()) {
                    CompletableFuture<Void> migration = CompletableFuture.runAsync(() -> {
                        try {
                            Instant start = Instant.now();
                            
                            // Simulate migration delay (network latency + handshake)
                            simulateMigrationDelay();
                            
                            // Update connection mapping
                            connectionMap.put(connectionId, plan.targetNode());
                            
                            Instant end = Instant.now();
                            migrationMetrics.put(connectionId, new ConnectionMetrics(
                                connectionId, start, end, true, null
                            ));
                            
                            completed.incrementAndGet();
                            
                        } catch (Exception e) {
                            migrationMetrics.put(connectionId, new ConnectionMetrics(
                                connectionId, Instant.now(), Instant.now(), 
                                false, e.getMessage()
                            ));
                            failed.incrementAndGet();
                        }
                    }, executor);
                    
                    migrations.add(migration);
                }
                
                // Wait for all migrations with timeout
                try {
                    CompletableFuture.allOf(migrations.toArray(new CompletableFuture[0]))
                        .get(plan.estimatedTime().multipliedBy(2).toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    result.timedOut = true;
                }
                
            } catch (Exception e) {
                result.exception = e;
            }
            
            result.endTime = Instant.now();
            result.successfulMigrations = completed.get();
            result.failedMigrations = failed.get();
            result.totalConnections = plan.connectionsToMove().size();
            
            return result;
        });
    }
    
    private void simulateMigrationDelay() {
        try {
            // Simulate network latency + TCP handshake
            Thread.sleep(ThreadLocalRandom.current().nextInt(5, 20));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public Map<String, ConnectionMetrics> getMigrationMetrics() {
        return new HashMap<>(migrationMetrics);
    }
    
    public static class MigrationResult {
        public Instant startTime;
        public Instant endTime;
        public int totalConnections;
        public int successfulMigrations;
        public int failedMigrations;
        public boolean timedOut;
        public Exception exception;
        
        public double getSuccessRate() {
            return totalConnections > 0 
                ? (successfulMigrations / (double) totalConnections) * 100 
                : 0.0;
        }
        
        public Duration getDuration() {
            return Duration.between(startTime, endTime);
        }
    }
}
