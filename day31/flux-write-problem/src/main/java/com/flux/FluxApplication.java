package com.flux;

import com.flux.dashboard.DashboardServer;

/**
 * Main application entry point.
 */
public class FluxApplication {
    
    public static void main(String[] args) throws Exception {
        System.out.println("""
            ╔══════════════════════════════════════════════════════════╗
            ║  Flux Day 31: The Write Problem                          ║
            ║  Postgres B-Trees vs LSM Tree Comparison                 ║
            ╚══════════════════════════════════════════════════════════╝
            """);
        
        var server = new DashboardServer(8080);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            server.stop();
        }));
        
        server.start();
        
        System.out.println("""
            
            ✓ Dashboard: http://localhost:8080
            ✓ Press Ctrl+C to stop
            
            Prerequisites:
            - Postgres running on localhost:5432
            - Database: fluxdb, User: postgres, Password: flux
            
            Quick Start:
            docker run --name flux-postgres \
              -e POSTGRES_PASSWORD=flux \
              -e POSTGRES_DB=fluxdb \
              -p 5432:5432 -d postgres:15
            """);
        
        Thread.currentThread().join();
    }
}
