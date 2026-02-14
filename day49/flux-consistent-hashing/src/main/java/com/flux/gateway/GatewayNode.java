package com.flux.gateway;

/**
 * Represents a Gateway node in the cluster.
 * Uses Java 21 record for immutability and zero-boilerplate.
 */
public record GatewayNode(String id, String host, int port) {
    
    public GatewayNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Node id cannot be null or blank");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host cannot be null or blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
    }
    
    @Override
    public String toString() {
        return id;
    }
    
    public String getAddress() {
        return host + ":" + port;
    }
}
