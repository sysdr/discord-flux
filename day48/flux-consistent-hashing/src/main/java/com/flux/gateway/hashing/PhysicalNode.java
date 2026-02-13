package com.flux.gateway.hashing;

/**
 * Represents a physical gateway node in the cluster.
 * Immutable by design for safe concurrent access.
 */
public record PhysicalNode(
    String nodeId,
    String address,
    int weight  // For weighted consistent hashing (homework)
) {
    public PhysicalNode(String nodeId, String address) {
        this(nodeId, address, 1);  // Default weight
    }
    
    @Override
    public String toString() {
        return "Node[" + nodeId + " @ " + address + (weight != 1 ? " w=" + weight : "") + "]";
    }
}
