package com.flux.tombstone;

public record MessageId(long value) implements Comparable<MessageId> {
    
    private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC
    private static long sequence = 0;
    private static long lastTimestamp = -1;
    
    public static synchronized MessageId generate() {
        long timestamp = System.currentTimeMillis() - EPOCH;
        
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards");
        }
        
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & 0xFFF; // 12 bits
            if (sequence == 0) {
                // Sequence overflow, wait for next millisecond
                while (timestamp <= lastTimestamp) {
                    timestamp = System.currentTimeMillis() - EPOCH;
                }
            }
        } else {
            sequence = 0;
        }
        
        lastTimestamp = timestamp;
        
        // 64-bit = [timestamp:41][datacenter:5][worker:5][sequence:12]
        long id = (timestamp << 22) | (1L << 17) | (1L << 12) | sequence;
        return new MessageId(id);
    }
    
    public long timestamp() {
        return (value >>> 22) + EPOCH;
    }
    
    @Override
    public int compareTo(MessageId other) {
        return Long.compareUnsigned(value, other.value);
    }
}
