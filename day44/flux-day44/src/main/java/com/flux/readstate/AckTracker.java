package com.flux.readstate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * The hot-path read state store.
 *
 * Stores up to MAX_ENTRIES in memory. In production, entries for offline
 * users would be evicted via LRU after a TTL. This lesson focuses on the
 * write coalescing path — eviction is left as homework.
 *
 * Write coalescing ratio: measured empirically as
 *   totalAcks / cassandraWrites
 * In idle traffic: 1:1. Under load burst: commonly 500:1 or higher.
 */
public final class AckTracker {

    private static final int MAX_ENTRIES   = 200_000;
    private static final int MAX_BATCH     = 500;

    // ── Primary store ──────────────────────────────────────────────
    private final ConcurrentHashMap<AckKey, ReadStateEntry> states
        = new ConcurrentHashMap<>(50_000, 0.75f, 64);

    // ── Dirty queue: set semantics collapse duplicate ack keys ─────
    private final Set<AckKey> dirtyQueue = ConcurrentHashMap.newKeySet(10_000);

    // ── Channel-level latest message index ─────────────────────────
    // Simulates what Cassandra/Redis would provide: the HEAD message ID
    private final ConcurrentHashMap<Long, Long> channelLatest = new ConcurrentHashMap<>();

    // ── Metrics ────────────────────────────────────────────────────
    private final LongAdder totalAcks      = new LongAdder();
    private final LongAdder staleAcks      = new LongAdder();
    private final LongAdder newEntries     = new LongAdder();
    private final AtomicLong cassandraWrites= new AtomicLong(0);
    private final AtomicLong cassandraBatches= new AtomicLong(0);

    // Rate tracking (snapshot windows)
    private volatile long lastMetricsAcks     = 0;
    private volatile long lastMetricsCassWrites= 0;
    private volatile long lastMetricsNs       = System.nanoTime();

    // ──────────────────────────────────────────────────────────────
    public AckResult ack(AckCommand cmd) {
        totalAcks.increment();

        var key = new AckKey(cmd.userId(), cmd.channelId());

        boolean[] created = {false};
        var entry = states.computeIfAbsent(key, k -> {
            created[0] = true;
            newEntries.increment();
            return new ReadStateEntry(k.userId(), k.channelId(), 0L);
        });

        if (created[0]) {
            // Bootstrap with latest channel position minus 1 so unread shows correctly
            long channelHead = channelLatest.getOrDefault(cmd.channelId(), cmd.messageId());
            // Still apply the ack to the new entry
            entry.tryAdvance(cmd.messageId());
            if (cmd.mentionDelta() > 0) entry.addMentions(cmd.mentionDelta());
            dirtyQueue.add(key);
            return AckResult.CREATED;
        }

        boolean advanced = entry.tryAdvance(cmd.messageId());
        if (!advanced) {
            staleAcks.increment();
            return AckResult.STALE;
        }

        if (cmd.mentionDelta() < 0) {
            entry.clearMentions(-cmd.mentionDelta());
        }

        dirtyQueue.add(key); // Set.add is idempotent — 1000 acks = 1 dirty entry
        return AckResult.ADVANCED;
    }

    /** Update the HEAD message pointer for a channel (called on new message). */
    public void onNewMessage(long channelId, long messageId) {
        channelLatest.merge(channelId, messageId,
            (existing, incoming) -> incoming > existing ? incoming : existing);
    }

    public ReadSnapshot getSnapshot(long userId, long channelId) {
        var key    = new AckKey(userId, channelId);
        var entry  = states.get(key);
        long lastRead = entry != null ? entry.getLastReadMessageId() : 0L;
        long latest   = channelLatest.getOrDefault(channelId, 0L);
        int  unread   = computeUnreadApprox(lastRead, latest, channelId);
        int  mentions = entry != null ? entry.getMentionCount() : 0;
        int  state    = entry != null ? entry.getState() : -1;
        return new ReadSnapshot(userId, channelId, lastRead, latest, unread, mentions,
            state, ReadSnapshot.labelFor(state));
    }

    /**
     * Drain up to MAX_BATCH dirty keys for Cassandra flush.
     * Thread-safe: iterator.remove() on ConcurrentHashMap.newKeySet() is safe.
     */
    public List<AckKey> drainDirtyBatch() {
        var batch = new ArrayList<AckKey>(MAX_BATCH);
        var iter  = dirtyQueue.iterator();
        while (iter.hasNext() && batch.size() < MAX_BATCH) {
            var key   = iter.next();
            var entry = states.get(key);
            if (entry != null && entry.tryBeginFlush()) {
                batch.add(key);
                iter.remove();
            } else {
                iter.remove(); // Entry evicted or not dirty — clean up
            }
        }
        return batch;
    }

    public void onFlushSuccess(List<AckKey> keys) {
        cassandraWrites.addAndGet(keys.size());
        cassandraBatches.incrementAndGet();
        keys.forEach(key -> {
            var entry = states.get(key);
            if (entry != null) entry.markClean();
        });
    }

    public void onFlushFailure(List<AckKey> keys) {
        // Re-enqueue failed keys
        keys.forEach(key -> {
            var entry = states.get(key);
            if (entry != null) {
                entry.markDirtyAfterFailure();
                dirtyQueue.add(key);
            }
        });
    }

    public AckMetrics getMetrics() {
        long nowNs      = System.nanoTime();
        long totalA     = totalAcks.sum();
        long totalCW    = cassandraWrites.get();
        double elapsedS = (nowNs - lastMetricsNs) / 1e9;
        double ackRate  = elapsedS > 0 ? (totalA - lastMetricsAcks) / elapsedS : 0;
        double cwRate   = elapsedS > 0 ? (totalCW - lastMetricsCassWrites) / elapsedS : 0;
        double ratio    = totalCW > 0 ? (double) totalA / totalCW : 0;

        // Advance window
        lastMetricsAcks      = totalA;
        lastMetricsCassWrites = totalCW;
        lastMetricsNs         = nowNs;

        return new AckMetrics(
            totalA,
            staleAcks.sum(),
            newEntries.sum(),
            dirtyQueue.size(),
            states.size(),
            totalCW,
            cassandraBatches.get(),
            ratio,
            ackRate,
            cwRate
        );
    }

    public List<EntrySnapshot> getEntrySnapshots(int limit) {
        var result = new ArrayList<EntrySnapshot>(limit);
        for (var entry : states.entrySet()) {
            if (result.size() >= limit) break;
            var e       = entry.getValue();
            long latest = channelLatest.getOrDefault(e.channelId, 0L);
            int unread  = computeUnreadApprox(e.getLastReadMessageId(), latest, e.channelId);
            result.add(new EntrySnapshot(e.userId, e.channelId, e.getState(),
                e.getMentionCount(), unread));
        }
        return result;
    }

    public ConcurrentHashMap<AckKey, ReadStateEntry> states() { return states; }
    public Set<AckKey> dirtyQueue() { return dirtyQueue; }
    public Map<Long, Long> channelLatest() { return channelLatest; }

    // ── Helpers ────────────────────────────────────────────────────
    private int computeUnreadApprox(long lastRead, long latest, long channelId) {
        if (latest == 0 || lastRead >= latest) return 0;
        // Approximate: in production, query Cassandra for exact count
        // Snowflake IDs encode ms timestamp — delta gives rough message count
        long msDelta = SnowflakeIdGenerator.timestampOf(latest)
                     - SnowflakeIdGenerator.timestampOf(lastRead);
        // Assume ~1 message per second average in active channel
        return (int) Math.min(99, Math.max(1, msDelta / 1000));
    }
}
