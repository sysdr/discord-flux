package com.flux.readstate;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 64-bit time-sortable ID generator.
 * Layout: [41-bit millis since epoch][10-bit nodeId][12-bit sequence]
 * Guarantees monotonic ordering within a node. At 1M IDs/sec, the
 * 12-bit sequence (4096) wraps — we spin on millisecond boundary.
 */
public final class SnowflakeIdGenerator {

    private static final long EPOCH_MS     = 1_700_000_000_000L; // Nov 2023
    private static final int  NODE_BITS    = 10;
    private static final int  SEQ_BITS     = 12;
    private static final long MAX_SEQ      = (1L << SEQ_BITS) - 1;
    private static final long NODE_SHIFT   = SEQ_BITS;
    private static final long TIME_SHIFT   = SEQ_BITS + NODE_BITS;

    private final long nodeId;
    private final AtomicLong state; // packed: [32-bit lastMs][12-bit seq] conceptually

    // We pack lastMillis + sequence into a single long for atomic CAS:
    // high 52 bits = last timestamp ms, low 12 bits = sequence
    private static final long SEQ_MASK  = MAX_SEQ;
    private static final long TIME_MASK = ~SEQ_MASK;

    public SnowflakeIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId >= (1L << NODE_BITS))
            throw new IllegalArgumentException("nodeId out of range [0, 1023]");
        this.nodeId = nodeId;
        this.state  = new AtomicLong((System.currentTimeMillis() - EPOCH_MS) << SEQ_BITS);
    }

    public long nextId() {
        while (true) {
            long current = state.get();
            long lastMs  = (current >> SEQ_BITS);
            long nowMs   = System.currentTimeMillis() - EPOCH_MS;

            long nextMs  = Math.max(nowMs, lastMs);
            long seq     = (nextMs == lastMs) ? (current & SEQ_MASK) + 1 : 0;

            if (seq > MAX_SEQ) {
                // Sequence exhausted — spin to next millisecond
                Thread.onSpinWait();
                continue;
            }

            long next = (nextMs << SEQ_BITS) | seq;
            if (state.compareAndSet(current, next)) {
                return (nextMs << TIME_SHIFT) | (nodeId << NODE_SHIFT) | seq;
            }
        }
    }

    /** Extract the UTC millisecond timestamp embedded in a Snowflake ID */
    public static long timestampOf(long id) {
        return (id >> TIME_SHIFT) + EPOCH_MS;
    }
}
