package com.flux.rebalancing;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Main simulator for demonstrating consistent hash ring rebalancing.
 * Manages cluster state and provides monitoring endpoints.
 */
public class RebalancingSimulator {
    
    private final ConsistentHashRing ring;
    private final ConnectionMigrator migrator;
    private final Map<String, GatewayNode> activeConnections;
    private final RebalanceDashboard dashboard;
    private final List<RebalanceEvent> eventLog;
    
    private static final int VIRTUAL_NODES = 150;
    
    public RebalancingSimulator() {
        this.ring = new ConsistentHashRing(VIRTUAL_NODES);
        this.migrator = new ConnectionMigrator(ring);
        this.activeConnections = new ConcurrentHashMap<>();
        this.eventLog = new CopyOnWriteArrayList<>();
        this.dashboard = new RebalanceDashboard(this);
    }
    
    public void initializeCluster(int initialNodeCount) {
        System.out.println("[ConsistentHashRing] Initializing with " + initialNodeCount + 
            " nodes, " + VIRTUAL_NODES + " virtual nodes each");
        
        for (int i = 1; i <= initialNodeCount; i++) {
            GatewayNode node = GatewayNode.create(
                "node-" + i,
                "10.0.0." + i,
                9000 + i
            ).withStatus(GatewayNode.NodeStatus.ACTIVE);
            
            ring.addNode(node);
            logEvent("CLUSTER_INIT", "Added " + node.nodeId() + " to ring");
        }
    }
    
    public void createConnections(int count) {
        System.out.println("[Simulator] Creating " + count + " active connections");
        
        for (int i = 0; i < count; i++) {
            String connectionId = "conn-" + UUID.randomUUID().toString().substring(0, 8);
            GatewayNode assignedNode = ring.getNodeForConnection(connectionId);
            activeConnections.put(connectionId, assignedNode);
        }
        
        logEvent("CONNECTIONS_CREATED", "Total connections: " + activeConnections.size());
        printLoadDistribution();
    }
    
    public CompletableFuture<ConnectionMigrator.MigrationResult> addNode(String nodeId) {
        System.out.println("\n[Rebalancing] Adding node: " + nodeId);
        
        GatewayNode newNode = GatewayNode.create(
            nodeId,
            "10.0.0." + (ring.getPhysicalNodeCount() + 1),
            9000 + (ring.getPhysicalNodeCount() + 1)
        ).withStatus(GatewayNode.NodeStatus.ACTIVE);
        
        // Calculate migration plan BEFORE adding to ring
        List<MigrationPlan> plans = migrator.planNodeAddition(newNode, activeConnections);
        
        int totalToMove = plans.stream()
            .mapToInt(p -> p.connectionsToMove().size())
            .sum();
        
        double movementRatio = (totalToMove / (double) activeConnections.size()) * 100;
        double theoreticalMinimum = (1.0 / (ring.getPhysicalNodeCount() + 1)) * 100;
        
        System.out.printf("[Migration] Connections to migrate: %d (%.2f%%)%n", 
            totalToMove, movementRatio);
        System.out.printf("[Migration] Theoretical minimum: %.2f%%%n", theoreticalMinimum);
        
        logEvent("NODE_ADDITION_PLANNED", String.format(
            "%s - Moving %d connections (%.2f%% vs %.2f%% theoretical)",
            nodeId, totalToMove, movementRatio, theoreticalMinimum
        ));
        
        // NOW add the node to ring
        ring.addNode(newNode);
        
        // Execute migrations if any
        if (plans.isEmpty()) {
            System.out.println("[Migration] No connections to migrate");
            return CompletableFuture.completedFuture(null);
        }
        
        // For simplicity, merge all plans into one
        Set<String> allConnections = plans.stream()
            .flatMap(p -> p.connectionsToMove().stream())
            .collect(Collectors.toSet());
        
        MigrationPlan consolidatedPlan = MigrationPlan.create(newNode, allConnections);
        
        return migrator.executeMigration(consolidatedPlan, activeConnections)
            .thenApply(result -> {
                System.out.printf("[Migration] Completed: %d/%d successful (%.2f%%)%n",
                    result.successfulMigrations, result.totalConnections, result.getSuccessRate());
                System.out.printf("[Migration] Duration: %s%n", result.getDuration());
                
                printLoadDistribution();
                
                logEvent("MIGRATION_COMPLETED", String.format(
                    "%d/%d successful in %s",
                    result.successfulMigrations,
                    result.totalConnections,
                    result.getDuration()
                ));
                
                return result;
            });
    }
    
    private void printLoadDistribution() {
        var distribution = activeConnections.values().stream()
            .collect(Collectors.groupingBy(
                GatewayNode::nodeId,
                Collectors.counting()
            ));
        
        System.out.println("\n[Load Distribution]");
        distribution.forEach((nodeId, count) -> {
            double percentage = (count / (double) activeConnections.size()) * 100;
            System.out.printf("  %s: %d connections (%.2f%%)%n", nodeId, count, percentage);
        });
        
        double variance = ring.calculateLoadVariance(activeConnections);
        System.out.printf("  Coefficient of Variation: %.4f (target: < 0.05)%n", variance);
    }
    
    private void logEvent(String type, String message) {
        eventLog.add(new RebalanceEvent(Instant.now(), type, message));
    }
    
    public ConsistentHashRing getRing() {
        return ring;
    }
    
    public Map<String, GatewayNode> getActiveConnections() {
        return new HashMap<>(activeConnections);
    }
    
    public List<RebalanceEvent> getEventLog() {
        return new ArrayList<>(eventLog);
    }
    
    public void startDashboard(int port) {
        dashboard.start(port);
        System.out.println("[Dashboard] HTTP server listening on http://localhost:" + port);
    }
    
    public void shutdown() {
        dashboard.stop();
    }
    
    public record RebalanceEvent(Instant timestamp, String type, String message) {}
    
    public static void main(String[] args) throws Exception {
        var simulator = new RebalancingSimulator();
        
        // Initialize cluster
        simulator.initializeCluster(3);
        simulator.createConnections(50000);
        
        // Start dashboard
        simulator.startDashboard(8080);
        
        System.out.println("\n✅ Simulator running. Dashboard: http://localhost:8080");
        System.out.println("   Press Ctrl+C to shutdown");
        
        // Keep alive
        Thread.currentThread().join();
    }
}
