package com.flux.gateway;

import com.flux.gateway.server.DashboardServer;
import com.flux.gateway.server.GatewayServer;

import java.io.IOException;

/**
 * Main entry point. Starts Gateway + Dashboard servers.
 */
public class Main {
    public static void main(String[] args) {
        try {
            GatewayServer gateway = new GatewayServer(9000);
            DashboardServer dashboard = new DashboardServer(8080, gateway);
            
            dashboard.start();
            
            // Start gateway (blocking)
            Thread.ofVirtual().start(gateway::start);
            
            System.out.println("\n🎯 Flux Gateway Ready!");
            System.out.println("   Gateway: localhost:9000");
            System.out.println("   Dashboard: http://localhost:8080");
            System.out.println("\nPress Ctrl+C to stop...\n");
            
            // Keep main thread alive
            Thread.currentThread().join();
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Fatal error: " + e.getMessage());
            System.exit(1);
        }
    }
}
