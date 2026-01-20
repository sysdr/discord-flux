package com.flux.serialization;

import com.flux.serialization.benchmark.BenchmarkRunner;
import com.flux.serialization.server.DashboardServer;

public class FluxSerializationApp {
    private static BenchmarkRunner runner;
    
    public static void main(String[] args) throws Exception {
        System.out.println("""
            ╔═══════════════════════════════════════════════════════╗
            ║   Flux Day 11: Serialization Benchmark               ║
            ║   Testing: JSON vs Protobuf vs Custom Binary         ║
            ╚═══════════════════════════════════════════════════════╝
        """);
        
        runner = new BenchmarkRunner();
        DashboardServer server = new DashboardServer(runner);
        
        // Start dashboard
        server.start();
        System.out.println("📊 Dashboard: http://localhost:8080");
        System.out.println("🚀 Trigger benchmark: curl http://localhost:8080/trigger");
        
        // Run benchmark automatically after 2 seconds
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                System.out.println("\n🚀 Auto-starting benchmark...");
                runner.runBenchmark();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        // Keep alive
        Thread.currentThread().join();
    }
}
