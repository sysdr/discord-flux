package com.flux.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConsistencyTest {
    private List<ReplicaNode> replicas;
    private CoordinatorNode coordinator;
    private SnowflakeGenerator idGen;
    
    @BeforeEach
    void setup() {
        replicas = List.of(
            new ReplicaNode(1),
            new ReplicaNode(2),
            new ReplicaNode(3)
        );
        coordinator = new CoordinatorNode(replicas);
        idGen = new SnowflakeGenerator(1);
    }
    
    @AfterEach
    void teardown() {
        replicas.forEach(ReplicaNode::shutdown);
    }
    
    @Test
    void testOneConsistencyWriteSucceeds() {
        Message msg = Message.create("channel-1", "user-1", "Test", idGen);
        WriteResult result = coordinator.write(msg, ConsistencyLevel.ONE);
        
        assertTrue(result.success());
        assertTrue(result.latencyMs() < 50, "ONE should be fast (<50ms)");
    }
    
    @Test
    void testQuorumConsistencyWriteSucceeds() {
        Message msg = Message.create("channel-1", "user-1", "Test", idGen);
        WriteResult result = coordinator.write(msg, ConsistencyLevel.QUORUM);
        
        assertTrue(result.success());
        assertTrue(result.latencyMs() < 100, "QUORUM should complete <100ms");
    }
    
    @Test
    void testQuorumFailsWithPartition() throws InterruptedException {
        // Partition 2 out of 3 nodes
        replicas.get(1).setPartitioned(true);
        replicas.get(2).setPartitioned(true);
        
        Thread.sleep(50); // Allow partition to take effect
        
        Message msg = Message.create("channel-1", "user-1", "Test", idGen);
        WriteResult result = coordinator.write(msg, ConsistencyLevel.QUORUM);
        
        assertFalse(result.success(), "QUORUM should fail when majority partitioned");
    }
    
    @Test
    void testSnowflakeIdsAreSortable() {
        long id1 = idGen.nextId();
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long id2 = idGen.nextId();
        
        assertTrue(id2 > id1, "Later IDs should be numerically greater");
        
        long timestamp1 = SnowflakeGenerator.extractTimestamp(id1);
        long timestamp2 = SnowflakeGenerator.extractTimestamp(id2);
        
        assertTrue(timestamp2 >= timestamp1, "Timestamps should be monotonic");
    }
}
