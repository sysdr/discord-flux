package com.flux.shard;

import com.flux.shard.dashboard.DashboardServer;
import com.flux.shard.gateway.GatewayNode;
import com.flux.shard.gateway.ShardDistributionTracker;
import com.flux.shard.generator.SnowflakeGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main application: Demonstrates shard routing with real-time visualization.
 */
public class FluxShardRouterApp {

    private static final int TOTAL_SHARDS = 64;
    private static final int DASHBOARD_PORT = 8080;

    public static void main(String[] args) throws IOException {
        System.out.println("=".repeat(60));
        System.out.println("Flux Shard Router - Day 52");
        System.out.println("=".repeat(60));

        // Initialize distribution tracker
        ShardDistributionTracker tracker = new ShardDistributionTracker(TOTAL_SHARDS);

        // Create gateway nodes (4 nodes, each handling 16 shards)
        List<GatewayNode> nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int start = i * 16;
            int end = start + 16;
            nodes.add(new GatewayNode("Gateway-" + i, start, end, TOTAL_SHARDS));
        }

        System.out.println("\nGateway Cluster:");
        nodes.forEach(node -> System.out.println("  " + node));

        // Start dashboard
        DashboardServer dashboard = new DashboardServer(DASHBOARD_PORT, tracker, TOTAL_SHARDS);
        dashboard.start();

        // Start background load generator so dashboard shows live data
        SnowflakeGenerator snowflake = new SnowflakeGenerator(1, 1);
        ScheduledExecutorService loadScheduler = Executors.newScheduledThreadPool(2);
        Runnable recordBatch = () -> {
            for (int i = 0; i < 50; i++) {
                long guildId = snowflake.nextId();
                tracker.recordEvent(guildId);
            }
        };
        loadScheduler.scheduleAtFixedRate(recordBatch, 1, 1, TimeUnit.SECONDS);
        System.out.println("\n✓ Background load: recording ~50 events/sec (dashboard will update)");

        System.out.println("\n✓ Dashboard: http://localhost:" + DASHBOARD_PORT);
        System.out.println("✓ Shard Router: Running");
        System.out.println("\nPress Ctrl+C to stop...\n");

        // Keep application running
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            loadScheduler.shutdown();
            try {
                loadScheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            dashboard.stop();
            var stats = tracker.getStats();
            System.out.println("\nFinal Statistics:");
            System.out.printf("  Mean: %.2f events/shard%n", stats.mean());
            System.out.printf("  Std Dev: %.2f (CV: %.1f%%)%n",
                stats.stdDev(), stats.coefficientOfVariation());
            System.out.printf("  Range: %d - %d%n", stats.min(), stats.max());
        }));

        // Keep main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
