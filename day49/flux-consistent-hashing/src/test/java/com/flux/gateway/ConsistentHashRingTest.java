package com.flux.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashRingTest {
    
    private ConsistentHashRing<GatewayNode> ring;
    private GatewayNode node1;
    private GatewayNode node2;
    private GatewayNode node3;
    
    @BeforeEach
    void setUp() {
        ring = new ConsistentHashRing<>(150);
        node1 = new GatewayNode("node-01", "10.0.0.1", 9001);
        node2 = new GatewayNode("node-02", "10.0.0.2", 9002);
        node3 = new GatewayNode("node-03", "10.0.0.3", 9003);
    }
    
    @Test
    void testAddNode() {
        ring.addNode(node1);
        assertEquals(150, ring.size());
        assertTrue(ring.getNodes().contains(node1));
    }
    
    @Test
    void testRemoveNode() {
        ring.addNode(node1);
        ring.addNode(node2);
        assertEquals(300, ring.size());
        
        ring.removeNode(node1);
        assertEquals(150, ring.size());
        assertFalse(ring.getNodes().contains(node1));
    }
    
    @Test
    void testConsistentRouting() {
        ring.addNode(node1);
        ring.addNode(node2);
        ring.addNode(node3);
        
        String key = "user_12345";
        GatewayNode firstRoute = ring.get(key);
        
        // Same key should always route to same node
        for (int i = 0; i < 100; i++) {
            assertEquals(firstRoute, ring.get(key));
        }
    }
    
    @Test
    void testDistribution() {
        ring.addNode(node1);
        ring.addNode(node2);
        ring.addNode(node3);
        
        // Generate 10000 random keys
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            keys.add("user_" + i);
        }
        
        Map<GatewayNode, Integer> distribution = ring.getDistribution(keys);
        
        // Each node should get roughly 33% of keys
        for (int count : distribution.values()) {
            double percentage = (count / 10000.0) * 100;
            assertTrue(percentage > 25 && percentage < 40, 
                "Distribution should be roughly even, got " + percentage + "%");
        }
    }
    
    @Test
    void testMinimalRebalancing() {
        ring.addNode(node1);
        ring.addNode(node2);
        ring.addNode(node3);
        
        // Record initial routing
        Map<String, GatewayNode> initialRouting = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            String key = "user_" + i;
            initialRouting.put(key, ring.get(key));
        }
        
        // Add a 4th node
        GatewayNode node4 = new GatewayNode("node-04", "10.0.0.4", 9004);
        ring.addNode(node4);
        
        // Count how many keys moved
        int moved = 0;
        for (Map.Entry<String, GatewayNode> entry : initialRouting.entrySet()) {
            if (!entry.getValue().equals(ring.get(entry.getKey()))) {
                moved++;
            }
        }
        
        // Only ~25% should move (from 3 nodes to 4)
        double movedPercentage = (moved / 10000.0) * 100;
        assertTrue(movedPercentage < 35, 
            "Only ~25% of keys should relocate, got " + movedPercentage + "%");
    }
    
    @Test
    void testEmptyRing() {
        assertNull(ring.get("any_key"));
    }
    
    @Test
    void testConcurrentReads() throws InterruptedException {
        ring.addNode(node1);
        ring.addNode(node2);
        
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            Thread t = Thread.ofVirtual().start(() -> {
                for (int j = 0; j < 1000; j++) {
                    String key = "user_" + threadId + "_" + j;
                    assertNotNull(ring.get(key));
                }
            });
            threads.add(t);
        }
        
        for (Thread t : threads) {
            t.join();
        }
    }
}
