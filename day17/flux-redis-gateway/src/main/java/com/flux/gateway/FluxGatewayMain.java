package com.flux.gateway;

/**
 * Main entry point for Flux Redis Gateway.
 * Starts WebSocket server and dashboard.
 */
public class FluxGatewayMain {
    public static void main(String[] args) {
        try {
            WebSocketGateway gateway = new WebSocketGateway();
            DashboardServer dashboard = new DashboardServer(gateway, 8080);
            
            dashboard.start();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🛑 Shutting down...");
                gateway.shutdown();
                dashboard.stop();
            }));
            
            gateway.start();
            
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
