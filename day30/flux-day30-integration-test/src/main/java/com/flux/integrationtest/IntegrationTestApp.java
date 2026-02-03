package com.flux.integrationtest;

import com.flux.integrationtest.gateway.FluxGateway;
import com.flux.integrationtest.client.LoadTestOrchestrator;
import com.flux.integrationtest.dashboard.DashboardServer;

/**
 * Main entry point for Day 30 Integration Test.
 * Starts Gateway, Dashboard, and Load Test orchestrator.
 */
public class IntegrationTestApp {
    public static void main(String[] args) throws Exception {
        int numClients = args.length >= 1 ? Integer.parseInt(args[0]) : 1000;
        int durationSeconds = args.length >= 2 ? Integer.parseInt(args[1]) : 60;
        
        System.out.println("""
        ╔═══════════════════════════════════════════════════════════════╗
        ║  FLUX DAY 30: INTEGRATION TEST                                ║
        ║  1,000-User Guild Chat Storm                                  ║
        ╚═══════════════════════════════════════════════════════════════╝
        """);
        
        // Start Gateway
        System.out.println("[Main] Starting Gateway...");
        FluxGateway gateway = new FluxGateway();
        gateway.start();
        
        Thread.sleep(2000); // Let gateway initialize
        
        // Start Load Test
        System.out.println("[Main] Initializing Load Test (" + numClients + " clients, " + durationSeconds + "s)...");
        LoadTestOrchestrator loadTest = new LoadTestOrchestrator(numClients, durationSeconds);
        
        // Start Dashboard
        System.out.println("[Main] Starting Dashboard...");
        DashboardServer dashboard = new DashboardServer();
        dashboard.start(gateway, loadTest);
        
        System.out.println("\n✅ All systems ready!");
        System.out.println("📊 Dashboard: http://localhost:9090/dashboard");
        System.out.println("🚀 Starting load test in 5 seconds...\n");
        
        Thread.sleep(5000);
        
        // Run load test
        loadTest.start();
        
        System.out.println("\n✅ Load test complete!");
        System.out.println("Press Ctrl+C to shutdown...");
        
        // Keep running for dashboard access
        Thread.sleep(Long.MAX_VALUE);
    }
}
