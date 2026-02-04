package com.flux.snowflake;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.time.Instant;

/**
 * Lock-free distributed ID generator using Twitter's Snowflake algorithm.
 * Generates 64-bit IDs with format: [timestamp(41) | worker(10) | sequence(12)]
 * 
 * Thread-safe for concurrent access via VarHandle CAS operations.
 */
public class SnowflakeGenerator {
    
    // Bit field sizes
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    
    // Maximum values
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1; // 1023
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;  // 4095
    
    // Bit shifts
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    
    // Custom epoch (January 1, 2015 00:00:00 UTC)
    private static final long EPOCH = 1420070400000L;
    
    // Maximum acceptable clock drift (5 seconds)
    private static final long MAX_BACKWARD_DRIFT_MS = 5000L;
    
    // VarHandles for lock-free atomic operations
    private static final VarHandle LAST_TIMESTAMP;
    private static final VarHandle SEQUENCE;
    private static final VarHandle CLOCK_DRIFT_EVENTS;
    private static final VarHandle SEQUENCE_EXHAUSTION_EVENTS;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            LAST_TIMESTAMP = lookup.findVarHandle(
                SnowflakeGenerator.class, "lastTimestamp", long.class);
            SEQUENCE = lookup.findVarHandle(
                SnowflakeGenerator.class, "sequence", long.class);
            CLOCK_DRIFT_EVENTS = lookup.findVarHandle(
                SnowflakeGenerator.class, "clockDriftEvents", long.class);
            SEQUENCE_EXHAUSTION_EVENTS = lookup.findVarHandle(
                SnowflakeGenerator.class, "sequenceExhaustionEvents", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private final long workerId;
    
    // Volatile fields for atomic access
    private volatile long lastTimestamp = -1L;
    private volatile long sequence = 0L;
    private volatile long clockDriftEvents = 0L;
    private volatile long sequenceExhaustionEvents = 0L;
    
    public SnowflakeGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                "Worker ID must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
    }
    
    /**
     * Generate next unique ID.
     * Thread-safe using synchronized for correctness under high concurrency.
     */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();
        
        // Handle clock drift
        if (timestamp < lastTimestamp) {
            long drift = lastTimestamp - timestamp;
            if (drift > MAX_BACKWARD_DRIFT_MS) {
                throw new ClockDriftException(
                    "Clock moved backwards by " + drift + "ms - exceeds max drift");
            }
            CLOCK_DRIFT_EVENTS.getAndAdd(this, 1L);
            timestamp = waitNextMillis(lastTimestamp);
        }
        
        if (timestamp == lastTimestamp) {
            // Same millisecond - increment sequence
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // Sequence exhaustion - wait for next millisecond
                SEQUENCE_EXHAUSTION_EVENTS.getAndAdd(this, 1L);
                timestamp = waitNextMillis(lastTimestamp);
                sequence = 0L;
            }
        } else {
            // New millisecond - reset sequence
            sequence = 0L;
        }
        
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
               | (workerId << WORKER_ID_SHIFT)
               | sequence;
    }
    
    /**
     * Spin-wait until next millisecond.
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            Thread.onSpinWait();
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }
    
    /**
     * Get current time in milliseconds.
     */
    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
    
    // Metrics getters
    public long getClockDriftEvents() {
        return (long) CLOCK_DRIFT_EVENTS.getAcquire(this);
    }
    
    public long getSequenceExhaustionEvents() {
        return (long) SEQUENCE_EXHAUSTION_EVENTS.getAcquire(this);
    }
    
    public long getWorkerId() {
        return workerId;
    }
    
    /**
     * Parse components from a Snowflake ID.
     */
    public static SnowflakeId parse(long id) {
        long timestamp = (id >> TIMESTAMP_SHIFT) + EPOCH;
        long workerId = (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
        long sequence = id & SEQUENCE_MASK;
        
        return new SnowflakeId(id, timestamp, workerId, sequence);
    }
}
