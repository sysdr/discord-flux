package com.flux.gateway.concurrency;

import com.flux.gateway.concurrency.common.ServerInterface;
import com.flux.gateway.concurrency.dashboard.DashboardServer;
import com.flux.gateway.concurrency.nioreactor.NioReactorServer;
import com.flux.gateway.concurrency.threadper.ThreadPerConnectionServer;
import com.flux.gateway.concurrency.virtual.VirtualThreadServer;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <thread|nio|virtual> [port]");
            System.err.println("  thread  - Thread-per-connection model");
            System.err.println("  nio     - NIO Reactor model");
            System.err.println("  virtual - Virtual Threads model");
            System.exit(1);
        }
        
        String type = args[0];
        int serverPort = args.length > 1 ? Integer.parseInt(args[1]) : 9000;
        int dashboardPort = 8080;
        
        ServerInterface server = switch (type) {
            case "thread" -> new ThreadPerConnectionServer(serverPort);
            case "nio" -> new NioReactorServer(serverPort);
            case "virtual" -> new VirtualThreadServer(serverPort);
            default -> throw new IllegalArgumentException("Unknown server type: " + type);
        };
        
        DashboardServer dashboard = new DashboardServer(dashboardPort);
        dashboard.setActiveServer(server);
        dashboard.start();
        
        server.start();
        
        System.out.println("\n=== Flux Day 7: Concurrency Models ===");
        System.out.println("Server type: " + server.getType());
        System.out.println("Server port: " + serverPort);
        System.out.println("Dashboard: http://localhost:" + dashboardPort);
        System.out.println("\nPress Ctrl+C to stop...\n");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            server.stop();
            dashboard.stop();
        }));
        
        Thread.currentThread().join();
    }
}
