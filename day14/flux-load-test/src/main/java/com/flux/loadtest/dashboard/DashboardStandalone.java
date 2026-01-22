package com.flux.loadtest.dashboard;

import com.flux.loadtest.metrics.MetricsCollector;

import java.io.IOException;

/**
 * Standalone dashboard server that runs indefinitely.
 * Useful for monitoring metrics even when load test is not running.
 */
public class DashboardStandalone {
    
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9090;
        
        MetricsCollector metrics = new MetricsCollector();
        metrics.startTest(); // Initialize with start time
        
        DashboardServer dashboard = new DashboardServer(port, metrics);
        dashboard.start();
        
        System.out.println("🚀 Dashboard Server Started");
        System.out.println("📊 Dashboard: http://localhost:" + port);
        System.out.println("📊 Metrics API: http://localhost:" + port + "/metrics");
        System.out.println("");
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
