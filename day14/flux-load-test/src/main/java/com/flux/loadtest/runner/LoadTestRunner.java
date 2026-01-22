package com.flux.loadtest.runner;

import com.flux.loadtest.client.WebSocketClient;
import com.flux.loadtest.client.ClientState;
import com.flux.loadtest.metrics.MetricsCollector;
import com.flux.loadtest.dashboard.DashboardServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main orchestrator for load testing.
 * Spawns 10k Virtual Thread clients in waves to avoid overwhelming the server.
 */
public class LoadTestRunner {
    
    private static final String TARGET_HOST = "localhost";
    private static final int TARGET_PORT = 8080;
    private static final int TOTAL_CLIENTS = 10_000;
    private static final int CLIENTS_PER_WAVE = 100;
    private static final long WAVE_INTERVAL_MS = 500;
    
    private final MetricsCollector metrics = new MetricsCollector();
    private final DashboardServer dashboard;
    private final List<WebSocketClient> clients = new ArrayList<>();
    
    public LoadTestRunner(int dashboardPort) throws IOException {
        this.dashboard = new DashboardServer(dashboardPort, metrics);
    }
    
    public void run() throws Exception {
        System.out.println("🚀 Starting Load Test: " + TOTAL_CLIENTS + " clients");
        System.out.println("📊 Dashboard: http://localhost:" + dashboard.getPort());
        
        dashboard.start();
        metrics.startTest();
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            int waves = TOTAL_CLIENTS / CLIENTS_PER_WAVE;
            
            for (int wave = 0; wave < waves; wave++) {
                final int waveNum = wave;
                
                for (int i = 0; i < CLIENTS_PER_WAVE; i++) {
                    final int clientId = (wave * CLIENTS_PER_WAVE) + i;
                    
                    executor.submit(() -> {
                        try {
                            WebSocketClient client = new WebSocketClient(clientId, metrics);
                            synchronized (clients) {
                                clients.add(client);
                            }
                            
                            client.connect(TARGET_HOST, TARGET_PORT);
                            
                            // Send 10 heartbeats, one every 5 seconds
                            // Only send if connection is open (handshake succeeded)
                            if (client.getState() instanceof ClientState.Open) {
                                client.sendHeartbeats(10, 5000);
                                
                                // Listen for incoming messages for 60 seconds
                                client.receiveMessages(60);
                            } else {
                                // Connection failed, but still record some activity
                                // Wait a bit to simulate connection attempt
                                Thread.sleep(100);
                            }
                            
                            client.close();
                            
                        } catch (Exception e) {
                            // Silently handle failures - metrics are already recorded
                            // Only log if it's not a handshake failure
                            if (!e.getMessage().contains("Handshake failed") && 
                                !e.getMessage().contains("connection not open")) {
                                System.err.println("Client " + clientId + " failed: " + e.getMessage());
                            }
                        }
                    });
                }
                
                System.out.printf("Wave %d/%d: Spawned %d clients (Total: %d)%n",
                    waveNum + 1, waves, CLIENTS_PER_WAVE, (waveNum + 1) * CLIENTS_PER_WAVE);
                
                Thread.sleep(WAVE_INTERVAL_MS);
            }
            
            System.out.println("✅ All clients spawned. Waiting for completion...");
            
            // Wait for all tasks to complete (timeout: 5 minutes)
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                System.err.println("⚠️  Timeout waiting for clients to finish");
                executor.shutdownNow();
            }
            
        } finally {
            metrics.endTest();
            printFinalReport();
            
            // Keep dashboard running indefinitely (user can stop with Ctrl+C)
            System.out.println("\n📊 Dashboard will remain running...");
            System.out.println("📊 View at: http://localhost:" + dashboard.getPort());
            System.out.println("Press Ctrl+C to stop the dashboard");
            
            // Keep running until interrupted
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                System.out.println("\n🛑 Stopping dashboard...");
                dashboard.stop();
                System.out.println("✅ Dashboard stopped");
            }
        }
    }
    
    private void printFinalReport() {
        var snapshot = metrics.getSnapshot();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("LOAD TEST FINAL REPORT");
        System.out.println("=".repeat(60));
        System.out.printf("Duration: %d seconds%n", snapshot.elapsedSeconds());
        System.out.printf("Total Attempts: %,d%n", snapshot.totalAttempts());
        System.out.printf("Successful: %,d (%.2f%%)%n", 
            snapshot.successfulConnections(), snapshot.successRate());
        System.out.printf("Failed: %,d%n", snapshot.failedConnections());
        System.out.printf("Messages Sent: %,d%n", snapshot.messagesSent());
        System.out.printf("Messages Received: %,d%n", snapshot.messagesReceived());
        System.out.printf("Connections/sec: %.2f%n", snapshot.connectionsPerSecond());
        System.out.printf("Heap Usage: %,d MB / %,d MB (%.1f%%)%n",
            snapshot.heapUsedBytes() / 1_048_576,
            snapshot.heapMaxBytes() / 1_048_576,
            snapshot.heapUsagePercent());
        System.out.printf("Active Threads: %d%n", snapshot.activeThreads());
        System.out.println("=".repeat(60));
    }
    
    public static void main(String[] args) throws Exception {
        int dashboardPort = args.length > 0 ? Integer.parseInt(args[0]) : 9090;
        
        LoadTestRunner runner = new LoadTestRunner(dashboardPort);
        runner.run();
    }
}
