package com.flux.rebalancing;

import java.time.Duration;
import java.util.Set;

public record MigrationPlan(
    GatewayNode targetNode,
    Set<String> connectionsToMove,
    Duration estimatedTime,
    MigrationStatus status
) {
    public enum MigrationStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
    
    public static MigrationPlan create(GatewayNode targetNode, Set<String> connections) {
        // Estimate: 100 connections/second migration rate
        long estimatedSeconds = Math.max(1, connections.size() / 100);
        return new MigrationPlan(
            targetNode,
            connections,
            Duration.ofSeconds(estimatedSeconds),
            MigrationStatus.PENDING
        );
    }
    
    public MigrationPlan withStatus(MigrationStatus newStatus) {
        return new MigrationPlan(targetNode, connectionsToMove, estimatedTime, newStatus);
    }
}
