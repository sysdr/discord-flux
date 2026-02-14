package com.flux.gateway;

import com.flux.gateway.ring.ConsistentHashRing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

class ConsistentHashRingTest {
    
    private ConsistentHashRing ring;
    
    @BeforeEach
    void setUp() {
        ring = new ConsistentHashRing(150);
    }
    
    @Test
    void testAddServer() {
        ring.addServer("server-01");
        assertEquals(1, ring.getServers().size());
        assertEquals(150, ring.getVirtualNodeCount());
    }
    
    @Test
    void testRemoveServer() {
        ring.addServer("server-01");
        ring.addServer("server-02");
        
        ring.removeServer("server-01");
        
        assertEquals(1, ring.getServers().size());
        assertEquals(150, ring.getVirtualNodeCount());
    }
    
    @Test
    void testFindServer() {
        ring.addServer("server-01");
        
        String serverId = ring.findServer("connection-123");
        assertEquals("server-01", serverId);
    }
    
    @Test
    void testDistributionWithMultipleServers() {
        // Add 10 servers
        for (int i = 1; i <= 10; i++) {
            ring.addServer("server-" + String.format("%02d", i));
        }
        
        // Route 50,000 connections (more connections = better distribution)
        for (int i = 0; i < 50_000; i++) {
            String connectionId = "conn-" + UUID.randomUUID();
            String serverId = ring.findServer(connectionId);
            ring.recordConnection(serverId);
        }
        
        var stats = ring.getStats();
        
        // Verify total
        assertEquals(50_000, stats.totalConnections());
        
        // Verify reasonable variance (with 150 vnodes; allow 15% for statistical variance)
        assertTrue(stats.variancePercent() < 15.0, 
            "Variance should be < 15% with virtual nodes, got: " + stats.variancePercent());
        
        // Verify each server received connections (no server should be empty)
        for (Map.Entry<String, Long> entry : stats.distribution().entrySet()) {
            long count = entry.getValue();
            assertTrue(count >= 3000 && count <= 7000, 
                "Server " + entry.getKey() + " has unusual count: " + count);
        }
    }
    
    @Test
    void testRebalancingOnServerAddition() {
        // Setup initial state
        for (int i = 1; i <= 5; i++) {
            ring.addServer("server-" + i);
        }
        
        // Route initial connections
        for (int i = 0; i < 5_000; i++) {
            String connectionId = "conn-" + i;
            String serverId = ring.findServer(connectionId);
            ring.recordConnection(serverId);
        }
        
        long initialTotal = ring.getStats().totalConnections();
        
        // Add new server
        ring.addServer("server-6");
        
        // Verify virtual nodes increased
        assertEquals(900, ring.getVirtualNodeCount()); // 6 * 150
        
        // Connection counts should still be tracked (not automatically redistributed in this impl)
        assertEquals(initialTotal, ring.getStats().totalConnections());
    }
    
    @Test
    void testEmptyRingThrowsException() {
        assertThrows(IllegalStateException.class, () -> {
            ring.findServer("any-connection");
        });
    }
    
    @Test
    void testConsistency() {
        ring.addServer("server-01");
        ring.addServer("server-02");
        
        String connectionId = "test-connection-123";
        
        // Same connection should always map to same server
        String server1 = ring.findServer(connectionId);
        String server2 = ring.findServer(connectionId);
        String server3 = ring.findServer(connectionId);
        
        assertEquals(server1, server2);
        assertEquals(server2, server3);
    }
}
