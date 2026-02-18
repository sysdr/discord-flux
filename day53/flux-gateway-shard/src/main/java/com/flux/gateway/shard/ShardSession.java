package com.flux.gateway.shard;

import com.flux.gateway.connection.ConnectionState;

import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents an active (or recently evicted) shard session.
 *
 * NOTE: The {@code state} field uses AtomicReference for lock-free state transitions
 * visible across threads — the ShardRegistry's compute() runs on an arbitrary thread,
 * while the connection's virtual thread may concurrently update state to ZOMBIE.
 *
 * We deliberately keep this as a class (not record) because {@code state} is mutable.
 */
public final class ShardSession {

    public final long          connectionId;
    public final ShardIdentity identity;
    public final String        sessionId;
    public final SocketChannel channel;
    public final Instant       connectedAt;
    public final AtomicReference<ConnectionState> state;

    public ShardSession(
            long connectionId,
            ShardIdentity identity,
            String sessionId,
            SocketChannel channel
    ) {
        this.connectionId = connectionId;
        this.identity     = identity;
        this.sessionId    = sessionId;
        this.channel      = channel;
        this.connectedAt  = Instant.now();
        this.state        = new AtomicReference<>(ConnectionState.IDENTIFIED);
    }

    /**
     * A session is considered a zombie if its channel is no longer open,
     * OR if its logical state has been marked ZOMBIE by the connection handler.
     */
    public boolean isZombie() {
        if (!channel.isOpen()) return true;
        var s = state.get();
        return s == ConnectionState.ZOMBIE || s == ConnectionState.DISCONNECTED;
    }

    /**
     * Attempts to transition state from {@code expected} to {@code next}.
     * Returns true if successful (CAS semantics).
     */
    public boolean transition(ConnectionState expected, ConnectionState next) {
        return state.compareAndSet(expected, next);
    }

    @Override
    public String toString() {
        return "ShardSession{id=%s, shard=%s, state=%s}".formatted(
                sessionId, identity, state.get());
    }
}
