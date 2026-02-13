package com.flux.discovery;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Immutable representation of a Gateway node in the cluster.
 * Uses record for zero-overhead data class (no hidden allocations).
 */
public record ServiceNode(
    String id,
    String host,
    int port,
    long registeredAt,
    NodeStatus status,
    ByteBuffer metadata // Off-heap metadata for large datasets
) {
    
    public ServiceNode {
        Objects.requireNonNull(id, "Node ID cannot be null");
        Objects.requireNonNull(host, "Host cannot be null");
        Objects.requireNonNull(status, "Status cannot be null");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
    }
    
    /**
     * Convenience constructor with minimal metadata.
     */
    public ServiceNode(String id, String host, int port) {
        this(id, host, port, System.currentTimeMillis(), 
             NodeStatus.HEALTHY, ByteBuffer.allocate(0));
    }
    
    /**
     * Creates a new node with updated status.
     */
    public ServiceNode withStatus(NodeStatus newStatus) {
        return new ServiceNode(id, host, port, registeredAt, newStatus, metadata);
    }
    
    /**
     * Returns the node's age in milliseconds.
     */
    public long ageMillis() {
        return System.currentTimeMillis() - registeredAt;
    }
    
    /**
     * Returns connection endpoint as "host:port".
     */
    public String endpoint() {
        return host + ":" + port;
    }
}

enum NodeStatus {
    REGISTERING,  // Initial registration in progress
    HEALTHY,      // Active and receiving heartbeats
    SUSPECTED,    // Missed heartbeats, under observation
    DEAD,         // Confirmed dead, to be evicted
    DEREGISTERED  // Gracefully removed
}
