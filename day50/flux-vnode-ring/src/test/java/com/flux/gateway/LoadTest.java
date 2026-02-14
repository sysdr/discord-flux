package com.flux.gateway;

import com.flux.gateway.ring.ConsistentHashRing;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load test to verify ring performance under high concurrency.
 * Run with: mvn test -Dtest=LoadTest
 */
class LoadTest {
    
    private static final int SERVER_COUNT = 10;
    private static final int CONNECTION_COUNT = 50_000;
    
    @Test
    void loadTest() throws InterruptedException {
        ConsistentHashRing ring = new ConsistentHashRing(150);
        
        // Add servers
        for (int i = 1; i <= SERVER_COUNT; i++) {
            ring.addServer("gateway-" + String.format("%02d", i));
        }
        
        // Run load test
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < CONNECTION_COUNT; i++) {
            executor.submit(() -> {
                String connectionId = "conn-" + UUID.randomUUID();
                String serverId = ring.findServer(connectionId);
                ring.recordConnection(serverId);
            });
        }
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.MINUTES));
        
        var stats = ring.getStats();
        assertEquals(CONNECTION_COUNT, stats.totalConnections());
        assertTrue(stats.variancePercent() < 15.0, "Variance should be < 15%, got: " + stats.variancePercent());
    }
}
