package com.flux.gateway.hashing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

class ConsistentHashRingTest {
    
    private ConsistentHashRing ring;
    
    @BeforeEach
    void setUp() {
        ring = new ConsistentHashRing(150);
    }
    
    @Test
    void testEmptyRingThrowsException() {
        assertThrows(IllegalStateException.class, () -> {
            ring.getNode("test-key");
        });
    }
    
    @Test
    void testSingleNode() {
        PhysicalNode node = new PhysicalNode("node-1", "10.0.0.1");
        ring.addNode(node);
        
        PhysicalNode result = ring.getNode("test-key");
        assertEquals(node.nodeId(), result.nodeId());
    }
    
    @Test
    void testMultipleNodes() {
        for (int i = 1; i <= 5; i++) {
            ring.addNode(new PhysicalNode("node-" + i, "10.0.0." + i));
        }
        
        assertEquals(5, ring.getPhysicalNodeCount());
        assertEquals(5 * 150, ring.getVirtualNodeCount());
    }
    
    @Test
    void testConsistentMapping() {
        for (int i = 1; i <= 3; i++) {
            ring.addNode(new PhysicalNode("node-" + i, "10.0.0." + i));
        }
        
        String key = "session:12345";
        PhysicalNode firstLookup = ring.getNode(key);
        PhysicalNode secondLookup = ring.getNode(key);
        
        assertEquals(firstLookup.nodeId(), secondLookup.nodeId());
    }
    
    @Test
    void testMinimalRedistributionOnNodeAdd() {
        // Setup initial ring
        List<PhysicalNode> initialNodes = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            initialNodes.add(new PhysicalNode("node-" + i, "10.0.0." + i));
        }
        ConsistentHashRing ringBefore = new ConsistentHashRing(initialNodes, 150);
        
        // Generate test keys
        List<String> keys = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < 10000; i++) {
            keys.add("session:" + random.nextLong());
        }
        
        // Add one more node
        List<PhysicalNode> newNodes = new ArrayList<>(initialNodes);
        newNodes.add(new PhysicalNode("node-11", "10.0.0.11"));
        ConsistentHashRing ringAfter = new ConsistentHashRing(newNodes, 150);
        
        // Calculate redistribution
        double redistPct = DistributionAnalyzer.calculateRedistributionPercentage(
            keys, ringBefore, ringAfter
        );
        
        // Adding 1 node to 10: new node gets ~1/11 of hash space, so ~9% keys move
        assertTrue(redistPct < 15.0,
            "Redistribution was " + redistPct + "%, expected < 15%");
    }
    
    @Test
    void testRemoveNode() {
        for (int i = 1; i <= 5; i++) {
            ring.addNode(new PhysicalNode("node-" + i, "10.0.0." + i));
        }
        
        Set<Long> affectedHashes = ring.removeNode("node-3");
        
        assertEquals(4, ring.getPhysicalNodeCount());
        assertEquals(4 * 150, ring.getVirtualNodeCount());
        assertFalse(affectedHashes.isEmpty());
    }
    
    @Test
    void testDistributionUniformity() {
        for (int i = 1; i <= 10; i++) {
            ring.addNode(new PhysicalNode("node-" + i, "10.0.0." + i));
        }
        
        Map<String, Integer> distribution = 
            DistributionAnalyzer.simulateDistribution(ring, 10000);
        
        double stdDev = DistributionAnalyzer.calculateStandardDeviation(distribution);
        double gini = DistributionAnalyzer.calculateGiniCoefficient(distribution);
        
        // With 150 virtual nodes, standard deviation should be reasonable (< 15%)
        assertTrue(stdDev < 15.0, "Standard deviation was " + stdDev + "%");
        
        // Gini coefficient should indicate reasonable uniformity (< 0.1)
        assertTrue(gini < 0.1, "Gini coefficient was " + gini);
    }
    
    @Test
    void testConcurrentReads() throws InterruptedException {
        for (int i = 1; i <= 5; i++) {
            ring.addNode(new PhysicalNode("node-" + i, "10.0.0." + i));
        }
        
        int threadCount = 10;
        int lookupsPerThread = 1000;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                Random random = new Random(threadId);
                for (int j = 0; j < lookupsPerThread; j++) {
                    String key = "session:" + random.nextLong();
                    assertDoesNotThrow(() -> ring.getNode(key));
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
    }
}
