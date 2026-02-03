package com.flux.integrationtest.client;

import com.flux.integrationtest.gateway.Message;
import com.flux.integrationtest.metrics.LatencyAggregator;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates 1,000+ Virtual Thread clients to simulate Guild chat storm.
 * Phases: RAMP_UP → STEADY_STATE → CHAOS → DRAIN → COMPLETE
 */
public class LoadTestOrchestrator {
    private final int totalClients;
    private final int testDurationSeconds;
    private final ExecutorService virtualThreadPool = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<Long, WebSocketSimulator> clients = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong totalMessagesSent = new AtomicLong(0);
    private final LatencyAggregator latencyAggregator = new LatencyAggregator();
    
    public enum Phase {
        IDLE,
        RAMP_UP,
        STEADY_STATE,
        CHAOS,
        DRAIN,
        COMPLETE
    }
    
    private volatile Phase currentPhase = Phase.IDLE;
    
    public LoadTestOrchestrator(int totalClients, int testDurationSeconds) {
        this.totalClients = totalClients;
        this.testDurationSeconds = testDurationSeconds;
    }
    
    public void start() throws Exception {
        System.out.println("[LoadTest] Starting with " + totalClients + " clients for " + testDurationSeconds + "s");
        
        // Phase 1: Ramp-up
        rampUp();
        
        // Phase 2: Steady state
        steadyState();
        
        // Phase 3: Chaos engineering
        chaos();
        
        // Phase 4: Drain
        drain();
        
        // Phase 5: Complete
        currentPhase = Phase.COMPLETE;
        printFinalReport();
    }
    
    private void rampUp() throws Exception {
        currentPhase = Phase.RAMP_UP;
        System.out.println("[LoadTest] Phase: RAMP_UP (0-30s)");
        
        int batchSize = 100;
        int batches = totalClients / batchSize;
        
        for (int batch = 0; batch < batches; batch++) {
            for (int i = 0; i < batchSize; i++) {
                long clientId = (batch * batchSize) + i + 1;
                virtualThreadPool.submit(() -> clientLifecycle(clientId));
            }
            
            Thread.sleep(1000); // 100 clients per second
            
            if (batch % 5 == 0) {
                System.out.println("[LoadTest] " + ((batch + 1) * batchSize) + " clients connected");
            }
        }
        
        System.out.println("[LoadTest] Ramp-up complete: " + totalClients + " clients connected");
    }
    
    private void steadyState() throws Exception {
        currentPhase = Phase.STEADY_STATE;
        int durationSec = Math.max(10, testDurationSeconds / 2);
        System.out.println("[LoadTest] Phase: STEADY_STATE (" + durationSec + "s)");
        
        long startTime = System.currentTimeMillis();
        long duration = durationSec * 1000L;
        
        while (System.currentTimeMillis() - startTime < duration) {
            Thread.sleep(5000);
            printMetrics();
        }
        
        System.out.println("[LoadTest] Steady state complete");
    }
    
    private void chaos() throws Exception {
        currentPhase = Phase.CHAOS;
        System.out.println("[LoadTest] Phase: CHAOS (inject failures)");
        
        Random random = new Random();
        int slowCount = Math.min(10, totalClients / 10);
        int disconnectCount = Math.min(100, totalClients / 5);
        
        if (slowCount > 0) {
            System.out.println("[LoadTest] Injecting " + slowCount + " slow consumers...");
            for (int i = 0; i < slowCount; i++) {
                long clientId = random.nextInt(totalClients) + 1;
                WebSocketSimulator client = clients.get(clientId);
                if (client != null) {
                    client.setReadDelay(5000);
                    System.out.println("[LoadTest] Client " + clientId + " is now slow consumer");
                }
            }
        }
        
        Thread.sleep(Math.min(10_000, testDurationSeconds * 200));
        
        if (disconnectCount > 0) {
            System.out.println("[LoadTest] Simulating reconnect storm (" + disconnectCount + " clients)...");
            List<Long> disconnectIds = new ArrayList<>();
            for (int i = 0; i < disconnectCount; i++) {
                long clientId = random.nextInt(totalClients) + 1;
                disconnectIds.add(clientId);
                WebSocketSimulator client = clients.get(clientId);
                if (client != null) {
                    client.close();
                }
            }
            
            Thread.sleep(2000);
            
            for (long clientId : disconnectIds) {
                virtualThreadPool.submit(() -> clientLifecycle(clientId));
            }
        }
        
        Thread.sleep(Math.min(10_000, testDurationSeconds * 200));
        System.out.println("[LoadTest] Chaos phase complete");
    }
    
    private void drain() throws Exception {
        currentPhase = Phase.DRAIN;
        System.out.println("[LoadTest] Phase: DRAIN");
        
        running.set(false);
        
        // Wait for clients to finish
        Thread.sleep(5000);
        
        // Close all clients
        clients.values().forEach(WebSocketSimulator::close);
        
        virtualThreadPool.shutdown();
        virtualThreadPool.awaitTermination(10, TimeUnit.SECONDS);
        
        System.out.println("[LoadTest] All clients disconnected");
    }
    
    private void clientLifecycle(long clientId) {
        try {
            WebSocketSimulator client = new WebSocketSimulator(clientId);
            client.connect();
            clients.put(clientId, client);
            
            // Start sender loop
            virtualThreadPool.submit(() -> senderLoop(client));
            
            // Start receiver loop
            receiverLoop(client);
            
        } catch (Exception e) {
            System.err.println("[LoadTest] Client " + clientId + " error: " + e.getMessage());
        }
    }
    
    private void senderLoop(WebSocketSimulator client) {
        Random random = new Random();
        
        while (running.get() && client.isConnected()) {
            try {
                Message msg = Message.chat(
                    client.getClientId(),
                    "Chat message from user " + client.getClientId()
                );
                
                client.send(msg);
                totalMessagesSent.incrementAndGet();
                
                // Random interval: 100-500ms
                Thread.sleep(100 + random.nextInt(400));
                
            } catch (Exception e) {
                break;
            }
        }
    }
    
    private void receiverLoop(WebSocketSimulator client) {
        while (running.get() && client.isConnected()) {
            try {
                Message msg = client.receive();
                if (msg != null) {
                    long receiveTime = System.nanoTime();
                    long latency = receiveTime - msg.timestamp();
                    latencyAggregator.record(latency);
                }
            } catch (Exception e) {
                break;
            }
        }
    }
    
    private void printMetrics() {
        LatencyAggregator.Percentiles latency = latencyAggregator.calculate();
        
        System.out.printf("[LoadTest] Metrics: Connections=%d, Messages Sent=%d, " +
                "Latency: P50=%.2fms, P95=%.2fms, P99=%.2fms%n",
            clients.size(),
            totalMessagesSent.get(),
            latency.p50Ms(),
            latency.p95Ms(),
            latency.p99Ms()
        );
    }
    
    private void printFinalReport() {
        LatencyAggregator.Percentiles latency = latencyAggregator.calculate();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("FINAL LOAD TEST REPORT");
        System.out.println("=".repeat(60));
        System.out.printf("Total Clients: %d%n", totalClients);
        System.out.printf("Total Messages Sent: %d%n", totalMessagesSent.get());
        System.out.printf("Total Latency Samples: %d%n", latencyAggregator.getTotalSamples());
        System.out.println("\nLatency Percentiles:");
        System.out.printf("  Average: %.2f ms%n", latency.avgMs());
        System.out.printf("  P50:     %.2f ms%n", latency.p50Ms());
        System.out.printf("  P95:     %.2f ms%n", latency.p95Ms());
        System.out.printf("  P99:     %.2f ms%n", latency.p99Ms());
        System.out.printf("  Max:     %.2f ms%n", latency.maxMs());
        
        System.out.println("\nPerformance Assessment:");
        if (latency.p95Ms() < 50.0) {
            System.out.println("  ✅ PASS: P95 latency < 50ms");
        } else {
            System.out.println("  ❌ FAIL: P95 latency > 50ms");
        }
        
        if (latency.p99Ms() < 100.0) {
            System.out.println("  ✅ PASS: P99 latency < 100ms");
        } else {
            System.out.println("  ❌ FAIL: P99 latency > 100ms");
        }
        
        System.out.println("=".repeat(60));
    }
    
    public Phase getCurrentPhase() { return currentPhase; }
    public Map<Long, WebSocketSimulator> getClients() { return clients; }
    public LatencyAggregator getLatencyAggregator() { return latencyAggregator; }
    public long getTotalMessagesSent() { return totalMessagesSent.get(); }
}
