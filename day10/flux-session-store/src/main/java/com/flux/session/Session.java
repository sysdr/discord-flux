package com.flux.session;

import java.net.InetSocketAddress;
import java.time.Instant;

public record Session(
    long sessionId,
    long userId,
    InetSocketAddress remoteAddress,
    Instant connectedAt,
    Instant lastActivity,
    SessionState state
) {
    public Session updateActivity() {
        return new Session(
            sessionId,
            userId,
            remoteAddress,
            connectedAt,
            Instant.now(),
            state
        );
    }

    public Session updateState(SessionState newState) {
        return new Session(
            sessionId,
            userId,
            remoteAddress,
            connectedAt,
            lastActivity,
            newState
        );
    }

    public boolean isStale(long idleTimeoutSeconds) {
        return lastActivity.plusSeconds(idleTimeoutSeconds)
                          .isBefore(Instant.now());
    }
}
