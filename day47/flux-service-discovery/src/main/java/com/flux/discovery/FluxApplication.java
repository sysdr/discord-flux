package com.flux.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application entry point.
 */
public class FluxApplication {
    
    private static final Logger log = LoggerFactory.getLogger(FluxApplication.class);
    
    public static void main(String[] args) {
        log.info("🚀 Starting Flux Service Discovery...");
        
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        int nodeCount = Integer.parseInt(System.getenv().getOrDefault("NODE_COUNT", "10"));
        
        try (ServiceRegistry registry = new ServiceRegistry(redisHost, redisPort)) {
            
            // Create simulator
            GatewaySimulator simulator = new GatewaySimulator(registry);
            
            // Spawn initial nodes
            simulator.spawnNodes(nodeCount);
            
            // Subscribe to events
            registry.subscribeToEvents(new NodeEventListener() {
                @Override
                public void onNodeJoined(String nodeId) {
                    log.info("📥 Node joined: {}", nodeId);
                }
                
                @Override
                public void onNodeLeft(String nodeId) {
                    log.info("📤 Node left: {}", nodeId);
                }
            });
            
            // Start dashboard
            Dashboard dashboard = new Dashboard(registry, simulator);
            dashboard.start();
            
            log.info("✅ Flux Service Discovery running");
            log.info("📊 Dashboard: http://localhost:8080");
            log.info("🔍 Active nodes: {}", registry.discover().size());
            
            // Keep running
            Thread.currentThread().join();
            
        } catch (Exception e) {
            log.error("Fatal error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
