package com.flux.readstate;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Lock-free mutable state for a single (userId, channelId) pair.
 *
 * The core invariant: lastReadMessageId is MONOTONICALLY INCREASING.
 * tryAdvance() uses a VarHandle CAS loop to enforce this atomically
 * across concurrent acks from the same user on multiple devices.
 *
 * VarHandle vs AtomicLong:
 *   - Eliminates one object allocation per field vs AtomicLong wrappers
 *   - Allows acquire/release semantics (lighter fence on ARM64)
 *   - Field offset is resolved at class-load time — zero runtime overhead
 *
 * Memory footprint: ~56 bytes per entry (object header + 5 fields + padding)
 * At 1M active entries: ~53 MB — acceptable for a gateway process
 */
public final class ReadStateEntry {

    volatile long lastReadMessageId;
    volatile int  mentionCount;
    volatile int  state;          // 0=CLEAN, 1=DIRTY, 2=FLUSHING
    volatile long lastUpdatedNs;

    public final long userId;
    public final long channelId;

    // ── State constants ─────────────────────────────────────────────
    public static final int STATE_CLEAN    = 0;
    public static final int STATE_DIRTY    = 1;
    public static final int STATE_FLUSHING = 2;

    // ── VarHandle lookup ─────────────────────────────────────────────
    private static final VarHandle LAST_READ_VH;
    private static final VarHandle STATE_VH;
    private static final VarHandle MENTION_VH;

    static {
        try {
            var lookup = MethodHandles.lookup();
            LAST_READ_VH = lookup.findVarHandle(ReadStateEntry.class, "lastReadMessageId", long.class);
            STATE_VH     = lookup.findVarHandle(ReadStateEntry.class, "state",             int.class);
            MENTION_VH   = lookup.findVarHandle(ReadStateEntry.class, "mentionCount",      int.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public ReadStateEntry(long userId, long channelId, long initialMessageId) {
        this.userId              = userId;
        this.channelId           = channelId;
        this.lastReadMessageId   = initialMessageId;
        this.state               = STATE_CLEAN;
        this.mentionCount        = 0;
        this.lastUpdatedNs       = System.nanoTime();
    }

    /**
     * Atomically advance the read pointer. Only moves forward.
     *
     * Thread safety: Multiple goroutine-equivalent Virtual Threads for the
     * same user can call this concurrently. The CAS loop guarantees:
     *   1. Only the highest messageId wins.
     *   2. No write is lost — every caller either succeeds or correctly
     *      detects that a higher-ID write already happened.
     *   3. Exactly one STATE_DIRTY write occurs per advancement cycle
     *      (the winner sets it; the loser observes the already-dirty state).
     *
     * @return true if state was actually advanced (triggers dirty marking)
     */
    public boolean tryAdvance(long newMessageId) {
        long current;
        do {
            current = (long) LAST_READ_VH.getAcquire(this);
            if (newMessageId <= current) {
                return false; // Stale or duplicate ack — silently drop
            }
        } while (!LAST_READ_VH.compareAndSet(this, current, newMessageId));

        // We won the CAS — we own the dirty flag transition
        STATE_VH.setRelease(this, STATE_DIRTY);
        lastUpdatedNs = System.nanoTime();
        return true;
    }

    /**
     * Decrement mention count when user acks a channel with mentions.
     * getAndAdd returns the *previous* value. Caller checks if it was > 0.
     */
    public int clearMentions(int count) {
        int prev;
        do {
            prev = (int) MENTION_VH.getAcquire(this);
            int next = Math.max(0, prev - count);
            if (MENTION_VH.compareAndSet(this, prev, next)) return prev;
        } while (true);
    }

    /** Atomically add mentions — called on new message with @mention. */
    public void addMentions(int delta) {
        MENTION_VH.getAndAdd(this, delta);
        STATE_VH.setRelease(this, STATE_DIRTY);
    }

    /** CAS from DIRTY → FLUSHING. Returns true if this thread owns the flush. */
    public boolean tryBeginFlush() {
        return STATE_VH.compareAndSet(this, STATE_DIRTY, STATE_FLUSHING);
    }

    /** Called after successful Cassandra batch write. */
    public void markClean() {
        STATE_VH.setRelease(this, STATE_CLEAN);
    }

    /** Called if Cassandra write failed — re-queue for retry. */
    public void markDirtyAfterFailure() {
        STATE_VH.compareAndSet(this, STATE_FLUSHING, STATE_DIRTY);
    }

    public long getLastReadMessageId() { return (long) LAST_READ_VH.getAcquire(this); }
    public int  getState()             { return (int)  STATE_VH.getAcquire(this);     }
    public int  getMentionCount()      { return (int)  MENTION_VH.getAcquire(this);   }
    public long getLastUpdatedNs()     { return lastUpdatedNs; }
}
