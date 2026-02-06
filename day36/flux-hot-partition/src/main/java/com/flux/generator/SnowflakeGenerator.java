package com.flux.generator;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Thread-safe Snowflake ID generator using VarHandle for lock-free atomicity.
 * 
 * ID Structure (64 bits):
 * - 41 bits: Timestamp (milliseconds since epoch)
 * - 10 bits: Worker ID (0-1023)
 * - 12 bits: Sequence number (0-4095)
 */
public final class SnowflakeGenerator {
    private static final long CUSTOM_EPOCH = 1609459200000L; // 2021-01-01 00:00:00 UTC
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private volatile long sequence = 0L;
    private volatile long lastTimestamp = -1L;

    private static final VarHandle SEQUENCE;
    private static final VarHandle LAST_TIMESTAMP;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SEQUENCE = lookup.findVarHandle(SnowflakeGenerator.class, "sequence", long.class);
            LAST_TIMESTAMP = lookup.findVarHandle(SnowflakeGenerator.class, "lastTimestamp", long.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public SnowflakeGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("Worker ID must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
    }

    /**
     * Generate the next Snowflake ID using lock-free CAS operations.
     */
    public long nextId() {
        long currentTimestamp = currentTimeMillis();
        long currentSequence;
        long currentLastTimestamp;

        // CAS loop to handle concurrent ID generation
        while (true) {
            currentLastTimestamp = (long) LAST_TIMESTAMP.getVolatile(this);

            if (currentTimestamp < currentLastTimestamp) {
                throw new RuntimeException("Clock moved backwards. Refusing to generate ID.");
            }

            if (currentTimestamp == currentLastTimestamp) {
                // Same millisecond - increment sequence
                currentSequence = (long) SEQUENCE.getVolatile(this);
                long nextSequence = (currentSequence + 1) & MAX_SEQUENCE;

                if (nextSequence == 0) {
                    // Sequence overflow - wait for next millisecond
                    currentTimestamp = waitForNextMillis(currentLastTimestamp);
                    continue;
                }

                if (SEQUENCE.compareAndSet(this, currentSequence, nextSequence)) {
                    currentSequence = nextSequence;
                    break;
                }
            } else {
                // New millisecond - reset sequence
                if (SEQUENCE.compareAndSet(this, SEQUENCE.getVolatile(this), 0L)) {
                    currentSequence = 0L;
                    if (LAST_TIMESTAMP.compareAndSet(this, currentLastTimestamp, currentTimestamp)) {
                        break;
                    }
                }
            }
        }

        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | currentSequence;
    }

    /**
     * Extract timestamp from a Snowflake ID.
     */
    public static long getTimestamp(long snowflakeId) {
        return ((snowflakeId >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH);
    }

    /**
     * Extract worker ID from a Snowflake ID.
     */
    public static long getWorkerId(long snowflakeId) {
        return (snowflakeId >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /**
     * Extract sequence number from a Snowflake ID.
     */
    public static long getSequence(long snowflakeId) {
        return snowflakeId & MAX_SEQUENCE;
    }

    private long waitForNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
