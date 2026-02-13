package com.flux.gateway.hashing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the Consistent Hashing demo.
 * Starts a cluster simulation with a live dashboard.
 */
public class ConsistentHashingDemo {
    
    public static void main(String[] args) throws IOException {
        System.out.println("=== Flux Consistent Hashing Demo ===\n");
        
        // Initialize ring with 10 nodes
        List<PhysicalNode> initialNodes = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String nodeId = "node-" + i;
            String address = "10.0.0." + i;
            initialNodes.add(new PhysicalNode(nodeId, address));
        }
        
        ConsistentHashRing ring = new ConsistentHashRing(initialNodes, 150);
        
        System.out.println("Initialized ring with " + ring.getPhysicalNodeCount() + " physical nodes");
        System.out.println("Total virtual nodes: " + ring.getVirtualNodeCount());
        
        // Analyze initial distribution
        var distribution = DistributionAnalyzer.simulateDistribution(ring, 10000);
        double stdDev = DistributionAnalyzer.calculateStandardDeviation(distribution);
        double gini = DistributionAnalyzer.calculateGiniCoefficient(distribution);
        
        System.out.println("\nInitial distribution (10,000 simulated keys):");
        System.out.printf("  Standard Deviation: %.2f%%\n", stdDev);
        System.out.printf("  Gini Coefficient: %.4f\n", gini);
        
        // Start dashboard
        System.out.println("\nStarting dashboard server...");
        DashboardServer dashboard = new DashboardServer(ring, 8080);
        dashboard.start();
        
        System.out.println("\n✓ Demo running!");
        System.out.println("  Dashboard: http://localhost:8080/dashboard");
        System.out.println("  Press Ctrl+C to stop");
        
        // Keep alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            dashboard.stop();
        }
    }
}
