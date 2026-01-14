package com.flux.gateway;

public final class FluxGatewayApp {
    public static void main(String[] args) {
        try {
            GatewayServer gateway = new GatewayServer(8080);
            DashboardServer dashboard = new DashboardServer(gateway);
            
            // Start dashboard first
            dashboard.start();
            
            // Start gateway (blocking)
            Thread.ofVirtual().start(() -> {
                try {
                    gateway.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            // Keep main thread alive
            Thread.sleep(Long.MAX_VALUE);
            
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
