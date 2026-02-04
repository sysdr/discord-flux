package com.flux;

import com.flux.dashboard.DashboardServer;
import com.flux.service.MessageService;
import com.flux.service.MetricsCollector;
import com.flux.service.ScyllaConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application entry point.
 * Initializes ScyllaDB connection, schema, and dashboard server.
 */
public class FluxApplication {
    private static final Logger logger = LoggerFactory.getLogger(FluxApplication.class);

    public static void main(String[] args) {
        logger.info("Starting Flux ScyllaDB Basics");
        
        try (ScyllaConnection connection = new ScyllaConnection("127.0.0.1", 9042, "datacenter1")) {
            
            // Initialize schema
            connection.initializeSchema();
            
            // Create services
            MessageService messageService = new MessageService(connection);
            MetricsCollector metrics = new MetricsCollector();
            
            // Start dashboard
            DashboardServer dashboard = new DashboardServer(8080, messageService, connection, metrics);
            dashboard.start();
            
            logger.info("Flux application running. Press Ctrl+C to stop.");
            
            // Keep application alive
            Thread.currentThread().join();
            
        } catch (Exception e) {
            logger.error("Application failed", e);
            System.exit(1);
        }
    }
}
