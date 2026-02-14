package com.flux.gateway;

import com.flux.gateway.ring.ConsistentHashRing;
import com.flux.gateway.server.DashboardServer;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main Gateway application demonstrating Virtual Node consistent hashing.
 */
public class FluxGateway {
    
    private static final int DASHBOARD_PORT = 8080;
    private static final int INITIAL_SERVER_COUNT = 10;
    private static final int VIRTUAL_NODES_PER_SERVER = 150;
    private static final int TEST_CONNECTION_COUNT = 100_000;
    
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("🚀 Flux Gateway - Virtual Node Ring Demo");
        System.out.println("==========================================\n");
        
        // Initialize the hash ring
        ConsistentHashRing ring = new ConsistentHashRing(VIRTUAL_NODES_PER_SERVER);
        
        // Add initial servers
        System.out.println("📡 Initializing gateway cluster...");
        for (int i = 1; i <= INITIAL_SERVER_COUNT; i++) {
            String serverId = String.format("gateway-%02d", i);
            ring.addServer(serverId);
            System.out.println("  ✓ Added " + serverId);
        }
        
        System.out.println("\n✅ Hash ring initialized:");
        System.out.println("   - Physical servers: " + ring.getServers().size());
        System.out.println("   - Virtual nodes: " + ring.getVirtualNodeCount());
        System.out.println();
        
        // Simulate connection routing
        System.out.println("🔄 Routing " + TEST_CONNECTION_COUNT + " test connections...");
        routeConnections(ring, TEST_CONNECTION_COUNT);
        
        // Display initial statistics
        printStats(ring);
        
        // Start dashboard server
        DashboardServer dashboard = new DashboardServer(DASHBOARD_PORT, ring);
        dashboard.start();
        
        System.out.println("\n🌐 Dashboard available at: http://localhost:" + DASHBOARD_PORT);
        System.out.println("💡 Press Ctrl+C to stop the server\n");
        
        // Keep application running
        Thread.currentThread().join();
    }
    
    private static void routeConnections(ConsistentHashRing ring, int count) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < count; i++) {
            executor.submit(() -> {
                String connectionId = "conn-" + UUID.randomUUID();
                String serverId = ring.findServer(connectionId);
                ring.recordConnection(serverId);
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("✓ Routed " + count + " connections in " + duration + "ms");
        System.out.println("  (" + (count * 1000 / Math.max(1, duration)) + " connections/sec)\n");
    }
    
    private static void printStats(ConsistentHashRing ring) {
        var stats = ring.getStats();
        
        System.out.println("📊 Distribution Statistics:");
        System.out.println("   Total Connections: " + stats.totalConnections());
        System.out.println("   Standard Deviation: " + String.format("%.2f", stats.stdDev()));
        System.out.println("   Variance: " + String.format("%.2f%%", stats.variancePercent()));
        System.out.println();
        
        System.out.println("📈 Per-Server Distribution:");
        stats.distribution().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                double percentage = (double) entry.getValue() / stats.totalConnections() * 100;
                System.out.printf("   %s: %,d (%.2f%%)%n", 
                    entry.getKey(), entry.getValue(), percentage);
            });
    }
}
