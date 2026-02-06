package com.flux.persistence;

import java.util.Optional;

public record ReadResult(
    int replicaId,
    Optional<Message> message,
    long latencyMs,
    boolean isStale
) {
    public ReadResult(int replicaId, Message message, long latencyMs) {
        this(replicaId, Optional.ofNullable(message), latencyMs, false);
    }
}
