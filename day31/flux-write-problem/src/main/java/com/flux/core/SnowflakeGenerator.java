package com.flux.core;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Lock-free Snowflake ID generator.
 * 64-bit structure: [41 bits timestamp][5 bits datacenter][5 bits worker][12 bits sequence]
 */
public class SnowflakeGenerator {
    
    private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC
    private static final long DATACENTER_BITS = 5L;
    private static final long WORKER_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    
    private static final long MAX_DATACENTER_ID = (1L << DATACENTER_BITS) - 1;
    private static final long MAX_WORKER_ID = (1L << WORKER_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;
    
    private final long datacenterId;
    private final long workerId;
    
    private volatile long lastTimestamp = -1L;
    private volatile int sequence = 0;
    
    private static final VarHandle SEQUENCE_HANDLE;
    private static final VarHandle LAST_TIMESTAMP_HANDLE;
    
    static {
        try {
            var lookup = MethodHandles.lookup();
            SEQUENCE_HANDLE = lookup.findVarHandle(SnowflakeGenerator.class, "sequence", int.class);
            LAST_TIMESTAMP_HANDLE = lookup.findVarHandle(SnowflakeGenerator.class, "lastTimestamp", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    public SnowflakeGenerator(long datacenterId, long workerId) {
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("Datacenter ID must be between 0 and " + MAX_DATACENTER_ID);
        }
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("Worker ID must be between 0 and " + MAX_WORKER_ID);
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }
    
    public synchronized long nextId() {
        long timestamp = currentTimestamp();
        
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards. Refusing to generate ID");
        }
        
        if (timestamp == lastTimestamp) {
            int seq = ((int) SEQUENCE_HANDLE.getAndAdd(this, 1)) & (int) MAX_SEQUENCE;
            if (seq == 0) {
                // Sequence exhausted, wait for next millisecond
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            SEQUENCE_HANDLE.setVolatile(this, 0);
        }
        
        LAST_TIMESTAMP_HANDLE.setVolatile(this, timestamp);
        
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
             | (datacenterId << DATACENTER_SHIFT)
             | (workerId << WORKER_SHIFT)
             | sequence;
    }
    
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimestamp();
        }
        return timestamp;
    }
    
    private long currentTimestamp() {
        return System.currentTimeMillis();
    }
    
    public static long extractTimestamp(long id) {
        return ((id >> TIMESTAMP_SHIFT) + EPOCH);
    }
    
    public static long extractDatacenterId(long id) {
        return (id >> DATACENTER_SHIFT) & MAX_DATACENTER_ID;
    }
    
    public static long extractWorkerId(long id) {
        return (id >> WORKER_SHIFT) & MAX_WORKER_ID;
    }
    
    public static long extractSequence(long id) {
        return id & MAX_SEQUENCE;
    }
}
