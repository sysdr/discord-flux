package com.flux.gateway.shard;

import com.flux.gateway.dashboard.MetricsCollector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of shard → session mappings.
 *
 * KEY DESIGN DECISION:
 * We use ConcurrentHashMap.compute() for atomic-per-key operations.
 * This gives us lock granularity at the shard-key level, not global.
 * Two different shards ([0,16] and [1,16]) can register simultaneously
 * without any contention.
 *
 * ZOMBIE DETECTION:
 * Before rejecting a new session for an occupied slot, we perform a
 * zero-byte write probe on the existing channel. A closed remote TCP
 * connection may still appear as channel.isOpen() == true on the local
 * side until the OS flushes the socket buffer. The write probe forces
 * the kernel to discover the broken pipe.
 */
public final class ShardRegistry {

    public sealed interface ClaimResult {
        record Claimed(ShardSession session) implements ClaimResult {}
        record Evicted(ShardSession session, ShardSession evicted) implements ClaimResult {}
        record Rejected(ShardSession existing) implements ClaimResult {}
    }

    private final ConcurrentHashMap<ShardIdentity, ShardSession> registry
        = new ConcurrentHashMap<>(64);

    private final MetricsCollector metrics = MetricsCollector.getInstance();

    /**
     * Attempts to claim ownership of a shard slot for the given session.
     *
     * Atomic per-key: concurrent claims for different shards do NOT contend.
     * Concurrent claims for the SAME shard identity are serialized by
     * ConcurrentHashMap's per-bucket striped lock.
     */
    public ClaimResult claim(ShardSession incoming) {
        final ShardIdentity key = incoming.identity;
        final ClaimResult[] result = new ClaimResult[1]; // captured by lambda

        registry.compute(key, (k, existing) -> {
            if (existing == null) {
                result[0] = new ClaimResult.Claimed(incoming);
                metrics.shardConnected();
                return incoming;
            }

            // Slot occupied — check if existing session is a zombie
            if (isZombieChannel(existing)) {
                closeQuietly(existing);
                metrics.shardZombieEvicted();
                result[0] = new ClaimResult.Evicted(incoming, existing);
                return incoming;
            }

            // Live session conflict — reject the newcomer
            result[0] = new ClaimResult.Rejected(existing);
            metrics.incrementIdentifyRejected();
            return existing; // keep existing
        });

        return result[0];
    }

    /**
     * Removes the session for the given shard identity only if it matches
     * the provided connectionId (prevents race where new session evicted
     * and old session's cleanup then removes the new one).
     */
    public boolean release(ShardIdentity identity, long connectionId) {
        final boolean[] removed = {false};
        registry.computeIfPresent(identity, (k, existing) -> {
            if (existing.connectionId == connectionId) {
                removed[0] = true;
                metrics.shardDisconnected();
                return null; // remove from map
            }
            return existing; // keep — belongs to a different (newer) connection
        });
        return removed[0];
    }

    public Collection<ShardSession> allSessions() {
        return registry.values();
    }

    public Map<ShardIdentity, ShardSession> snapshot() {
        return Map.copyOf(registry);
    }

    public int activeCount() {
        return (int) registry.values().stream()
            .filter(s -> !s.isZombie())
            .count();
    }

    // ── Zombie Detection ──────────────────────────────────────────────────

    /**
     * Performs a zero-byte write probe to detect broken TCP connections.
     * channel.isOpen() alone is insufficient — the OS may not have discovered
     * the broken pipe until a write is attempted.
     */
    private boolean isZombieChannel(ShardSession session) {
        if (!session.channel.isOpen()) return true;
        if (session.isZombie()) return true;
        try {
            // Zero-byte write — triggers RST discovery from OS
            session.channel.write(ByteBuffer.allocate(0));
            return false; // write succeeded, channel is alive
        } catch (IOException e) {
            return true; // broken pipe confirmed
        }
    }

    private void closeQuietly(ShardSession session) {
        try { session.channel.close(); } catch (IOException ignored) {}
    }
}
