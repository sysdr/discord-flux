package com.flux.loadbalancer.models;

import java.time.Instant;

public record GatewayNode(
    String nodeId,
    String ipAddress,
    int port,
    int currentConnections,
    Instant lastHeartbeat,
    String status
) {
    public boolean isHealthy() {
        var now = Instant.now();
        var secondsSinceHeartbeat = now.getEpochSecond() - lastHeartbeat.getEpochSecond();
        return "HEALTHY".equals(status) && secondsSinceHeartbeat < 15;
    }
    
    public boolean isDraining() {
        return "DRAINING".equals(status);
    }
}
