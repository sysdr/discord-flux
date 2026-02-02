package com.flux.presence;

import com.flux.presence.core.PresenceService;
import com.flux.presence.dashboard.DashboardHandler;
import com.flux.presence.server.WebSocketGateway;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * Main application entry point.
 */
public class FluxPresenceApp {
    
    private static final Logger logger = Logger.getLogger(FluxPresenceApp.class.getName());
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final int DASHBOARD_PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        logger.info("Starting Flux Presence System");
        
        // Initialize presence service
        PresenceService presenceService = new PresenceService(REDIS_HOST, REDIS_PORT);
        
        // Start HTTP dashboard
        HttpServer dashboardServer = HttpServer.create(new InetSocketAddress(DASHBOARD_PORT), 0);
        dashboardServer.createContext("/", new DashboardHandler(presenceService));
        dashboardServer.setExecutor(null);
        dashboardServer.start();
        logger.info("Dashboard started on http://localhost:" + DASHBOARD_PORT);
        
        // Start WebSocket Gateway
        WebSocketGateway gateway = new WebSocketGateway(presenceService);
        gateway.start();
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down...");
            try {
                gateway.close();
                presenceService.close();
                dashboardServer.stop(0);
            } catch (Exception e) {
                logger.severe("Shutdown error: " + e.getMessage());
            }
        }));
        
        logger.info("System ready. Press Ctrl+C to stop.");
        Thread.currentThread().join();
    }
}
