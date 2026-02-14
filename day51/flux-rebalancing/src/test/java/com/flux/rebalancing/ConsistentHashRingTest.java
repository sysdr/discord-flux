package com.flux.rebalancing;

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
    void testNodeAddition() {
        GatewayNode node1 = GatewayNode.create("node-1", "10.0.0.1", 9001);
        ring.addNode(node1);
        
        assertEquals(1, ring.getPhysicalNodeCount());
        assertEquals(150, ring.getRingSize());
    }
    
    @Test
    void testConsistentMapping() {
        GatewayNode node1 = GatewayNode.create("node-1", "10.0.0.1", 9001);
        GatewayNode node2 = GatewayNode.create("node-2", "10.0.0.2", 9002);
        
        ring.addNode(node1);
        ring.addNode(node2);
        
        // Same connection ID should always map to same node
        String connectionId = "test-conn-123";
        GatewayNode assigned1 = ring.getNodeForConnection(connectionId);
        GatewayNode assigned2 = ring.getNodeForConnection(connectionId);
        
        assertEquals(assigned1.nodeId(), assigned2.nodeId());
    }
    
    @Test
    void testMinimalKeyMovement() {
        // Add 3 nodes
        for (int i = 1; i <= 3; i++) {
            ring.addNode(GatewayNode.create("node-" + i, "10.0.0." + i, 9000 + i));
        }
        
        // Create connections and track assignments
        Map<String, GatewayNode> originalAssignments = new HashMap<>();
        int totalConnections = 10000;
        
        for (int i = 0; i < totalConnections; i++) {
            String connId = "conn-" + i;
            originalAssignments.put(connId, ring.getNodeForConnection(connId));
        }
        
        // Add 4th node
        ring.addNode(GatewayNode.create("node-4", "10.0.0.4", 9004));
        
        // Count how many changed
        int moved = 0;
        for (var entry : originalAssignments.entrySet()) {
            GatewayNode newAssignment = ring.getNodeForConnection(entry.getKey());
            if (!entry.getValue().nodeId().equals(newAssignment.nodeId())) {
                moved++;
            }
        }
        
        double movementRatio = (moved / (double) totalConnections);
        double theoreticalMinimum = 1.0 / 4.0; // 25%
        
        // Allow 5% variance due to hash distribution randomness
        assertTrue(movementRatio <= theoreticalMinimum + 0.05,
            "Movement ratio " + movementRatio + " exceeds theoretical minimum " + theoreticalMinimum);
    }
    
    @Test
    void testLoadDistribution() {
        // Add 5 nodes
        for (int i = 1; i <= 5; i++) {
            ring.addNode(GatewayNode.create("node-" + i, "10.0.0." + i, 9000 + i));
        }
        
        // Distribute 50000 connections
        Map<String, GatewayNode> connections = new HashMap<>();
        for (int i = 0; i < 50000; i++) {
            String connId = "conn-" + i;
            connections.put(connId, ring.getNodeForConnection(connId));
        }
        
        // Check variance
        double variance = ring.calculateLoadVariance(connections);
        
        assertTrue(variance < 0.10, 
            "Load variance " + variance + " is too high (target: < 0.10)");
    }
}
