package com.flux.model;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

public record SnowflakeId(long value) implements Comparable<SnowflakeId> {
    private static final long EPOCH = 1704067200000L; // 2024-01-01 UTC
    private static final long MACHINE_ID;
    private static final AtomicInteger sequence = new AtomicInteger(0);
    
    static {
        try {
            byte[] addr = InetAddress.getLocalHost().getAddress();
            MACHINE_ID = ((addr[2] & 0xFF) << 2) | (addr[3] & 0x03);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get machine ID", e);
        }
    }
    
    public static SnowflakeId generate() {
        long timestamp = System.currentTimeMillis() - EPOCH;
        long seq = sequence.getAndIncrement() & 0xFFF; // 12 bits
        long id = (timestamp << 22) | (MACHINE_ID << 12) | seq;
        return new SnowflakeId(id);
    }
    
    public static SnowflakeId fromTimestamp(long timestampMillis) {
        long adjustedTs = timestampMillis - EPOCH;
        return new SnowflakeId(adjustedTs << 22);
    }
    
    public long timestampMillis() {
        return ((value >> 22) & 0x1FFFFFFFFFFL) + EPOCH;
    }
    
    public int machineId() {
        return (int) ((value >> 12) & 0x3FF);
    }
    
    public int sequence() {
        return (int) (value & 0xFFF);
    }
    
    @Override
    public int compareTo(SnowflakeId other) {
        return Long.compare(this.value, other.value);
    }
    
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
