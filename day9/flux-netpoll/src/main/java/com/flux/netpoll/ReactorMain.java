package com.flux.netpoll;

public class ReactorMain {
    public static void main(String[] args) throws Exception {
        int reactorPort = 9090;
        int dashboardPort = 8080;
        
        System.out.println("=== FLUX NETPOLL REACTOR ===");
        System.out.println("Starting reactor on port " + reactorPort);
        System.out.println("Dashboard will be available at http://localhost:" + dashboardPort + "/dashboard");
        
        ReactorLoop reactor = new ReactorLoop(reactorPort);
        DashboardServer dashboard = new DashboardServer(dashboardPort, reactor);
        
        dashboard.start();
        
        // Start reactor in separate thread
        Thread reactorThread = Thread.ofPlatform().start(reactor);
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n⚠ Shutting down...");
            reactor.shutdown();
            dashboard.stop();
        }));
        
        reactorThread.join();
    }
}
