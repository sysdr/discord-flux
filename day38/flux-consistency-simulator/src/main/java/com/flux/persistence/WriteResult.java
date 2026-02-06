package com.flux.persistence;

public record WriteResult(
    int replicaId,
    boolean success,
    long latencyMs,
    String error
) {
    public WriteResult(int replicaId, boolean success) {
        this(replicaId, success, 0, null);
    }
    
    public WriteResult(int replicaId, boolean success, long latencyMs) {
        this(replicaId, success, latencyMs, null);
    }
}
