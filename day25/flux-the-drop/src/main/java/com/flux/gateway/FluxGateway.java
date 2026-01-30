package com.flux.gateway;

import com.flux.gateway.server.WebSocketServer;
import com.flux.gateway.reaper.ReaperThread;
import com.flux.gateway.dashboard.Dashboard;

public class FluxGateway {
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("Flux Gateway - The Drop");
        System.out.println("========================================");

        WebSocketServer server = new WebSocketServer();
        Thread serverThread = Thread.ofPlatform().start(server);

        ReaperThread reaper = new ReaperThread(server);
        Thread reaperThread = Thread.ofPlatform().start(reaper);

        Dashboard dashboard = new Dashboard(server, reaper);

        // Broadcast every 10ms so slow clients (2 msg/sec) fall behind; buffers fill in ~25–30s → lag & drops visible on dashboard
        Thread broadcasterThread = Thread.ofVirtual().start(() -> {
            int messageCount = 0;
            while (true) {
                try {
                    Thread.sleep(10);
                    server.broadcast("Guild message #" + (++messageCount));
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        System.out.println("\n[SUCCESS] All systems online!");
        System.out.println("  - WebSocket Gateway: ws://localhost:9090");
        System.out.println("  - Dashboard: http://localhost:8080");
        System.out.println("\nPress Ctrl+C to shutdown...\n");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SHUTDOWN] Gracefully stopping...");
            reaper.shutdown();
            server.shutdown();
            dashboard.shutdown();
        }));

        serverThread.join();
    }
}
