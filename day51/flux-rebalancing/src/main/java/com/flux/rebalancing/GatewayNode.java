package com.flux.rebalancing;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable representation of a Gateway node in the cluster.
 * Uses records for zero-cost abstraction and pattern matching support.
 */
public record GatewayNode(
    String nodeId,
    String address,
    int port,
    Instant joinedAt,
    NodeStatus status
) {
    private static final AtomicLong connectionCounter = new AtomicLong(0);
    
    public enum NodeStatus {
        JOINING,    // Node is being added to ring
        ACTIVE,     // Accepting connections
        DRAINING,   // Rejecting new connections, migrating existing
        DEAD        // Removed from ring
    }
    
    public static GatewayNode create(String nodeId, String address, int port) {
        return new GatewayNode(nodeId, address, port, Instant.now(), NodeStatus.JOINING);
    }
    
    public GatewayNode withStatus(NodeStatus newStatus) {
        return new GatewayNode(nodeId, address, port, joinedAt, newStatus);
    }
    
    public String getVirtualNodeKey(int virtualIndex) {
        return nodeId + ":vnode:" + virtualIndex;
    }
    
    public long allocateConnection() {
        return connectionCounter.incrementAndGet();
    }
}
