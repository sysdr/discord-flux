package com.flux.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Simulates multiple Gateway nodes registering and heartbeating.
 */
public class GatewaySimulator {
    
    private static final Logger log = LoggerFactory.getLogger(GatewaySimulator.class);
    
    private final ServiceRegistry registry;
    private final List<ServiceNode> simulatedNodes;
    private final Random random;
    
    public GatewaySimulator(ServiceRegistry registry) {
        this.registry = registry;
        this.simulatedNodes = new ArrayList<>();
        this.random = new Random();
    }
    
    /**
     * Spawn N gateway nodes.
     */
    public void spawnNodes(int count) {
        log.info("Spawning {} gateway nodes...", count);
        
        for (int i = 0; i < count; i++) {
            String nodeId = "gateway-" + String.format("%03d", i);
            String host = "10.0." + (i / 256) + "." + (i % 256);
            int port = 9000 + i;
            
            ServiceNode node = new ServiceNode(nodeId, host, port);
            simulatedNodes.add(node);
            
            // Register with slight jitter to simulate real deployment
            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(random.nextInt(100)); // 0-100ms jitter
                    registry.register(node);
                } catch (Exception e) {
                    log.error("Failed to register {}: {}", nodeId, e.getMessage());
                }
            });
        }
        
        log.info("Spawned {} nodes", count);
    }
    
    /**
     * Simulate random node crashes.
     */
    public void simulateCrashes(int count) {
        log.info("Simulating {} node crashes...", count);
        
        List<ServiceNode> candidates = new ArrayList<>(simulatedNodes);
        for (int i = 0; i < Math.min(count, candidates.size()); i++) {
            ServiceNode node = candidates.remove(random.nextInt(candidates.size()));
            registry.deregister(node.id());
            simulatedNodes.remove(node);
            log.info("Crashed node: {}", node.id());
        }
    }
    
    /**
     * Simulate registration storm (like K8s rolling restart).
     */
    public void simulateRegistrationStorm(int count) {
        log.info("Simulating registration storm ({} nodes)...", count);
        
        long startTime = System.nanoTime();
        
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int idx = i;
            Thread thread = Thread.startVirtualThread(() -> {
                String nodeId = "storm-" + String.format("%04d", idx);
                String host = "192.168." + (idx / 256) + "." + (idx % 256);
                int port = 10000 + idx;
                
                ServiceNode node = new ServiceNode(nodeId, host, port);
                registry.register(node);
            });
            threads.add(thread);
        }
        
        // Wait for all registrations
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        long duration = System.nanoTime() - startTime;
        double rps = count / (duration / 1_000_000_000.0);
        
        log.info("Registration storm completed: {} nodes in {}ms ({} reg/sec)",
            count, TimeUnit.NANOSECONDS.toMillis(duration), String.format("%.2f", rps));
    }
    
    /**
     * Get current simulated nodes.
     */
    public List<ServiceNode> getSimulatedNodes() {
        return new ArrayList<>(simulatedNodes);
    }
}
