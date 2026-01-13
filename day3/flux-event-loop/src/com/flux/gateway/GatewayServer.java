package com.flux.gateway;

import com.flux.gateway.core.EventLoop;
import com.flux.gateway.dashboard.Dashboard;
import java.io.IOException;

/**
 * Entry point for the Flux Gateway server.
 * 
 * Starts:
 * 1. EventLoop on port 9090 (client connections)
 * 2. Dashboard on port 8080 (metrics HTTP endpoint)
 */
public class GatewayServer {
    
    private static final int GATEWAY_PORT = 9090;
    private static final int DASHBOARD_PORT = 8080;
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   FLUX GATEWAY - Event Loop (Day 3)   ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        try {
            // Create event loop
            EventLoop eventLoop = new EventLoop(GATEWAY_PORT);
            
            // Create dashboard
            Dashboard dashboard = new Dashboard(DASHBOARD_PORT, eventLoop);
            dashboard.start();
            
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n⚠ Shutdown signal received");
                eventLoop.stop();
                dashboard.stop();
                System.out.println("✓ Graceful shutdown complete");
            }));
            
            // Start event loop (blocks)
            Thread eventLoopThread = new Thread(eventLoop, "EventLoop");
            eventLoopThread.start();
            
            System.out.println("\n✓ All systems operational");
            System.out.println("  → Gateway: localhost:" + GATEWAY_PORT);
            System.out.println("  → Dashboard: http://localhost:" + DASHBOARD_PORT);
            System.out.println("\nPress Ctrl+C to stop\n");
            
            eventLoopThread.join();
            
        } catch (IOException e) {
            System.err.println("❌ Failed to start server: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ Server interrupted");
            System.exit(1);
        }
    }
}
