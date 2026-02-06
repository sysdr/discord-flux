package com.flux.persistence;

import java.util.concurrent.atomic.AtomicLong;

public class SnowflakeGenerator {
    private static final long EPOCH = 1704067200000L; // Jan 1, 2024 00:00:00 UTC
    private final int nodeId;
    private final AtomicLong sequence = new AtomicLong(0);
    
    public SnowflakeGenerator(int nodeId) {
        if (nodeId < 0 || nodeId > 1023) {
            throw new IllegalArgumentException("Node ID must be between 0 and 1023");
        }
        this.nodeId = nodeId;
    }
    
    public long nextId() {
        long timestamp = System.currentTimeMillis() - EPOCH;
        long seq = sequence.getAndIncrement() & 0xFFF; // 12 bits (0-4095)
        
        // 41 bits timestamp | 10 bits node | 12 bits sequence
        return (timestamp << 22) | ((long) nodeId << 12) | seq;
    }
    
    public static long extractTimestamp(long snowflakeId) {
        return (snowflakeId >> 22) + EPOCH;
    }
    
    public static int extractNodeId(long snowflakeId) {
        return (int) ((snowflakeId >> 12) & 0x3FF);
    }
}
