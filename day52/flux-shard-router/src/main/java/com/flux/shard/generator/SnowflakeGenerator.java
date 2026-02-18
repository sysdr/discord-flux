package com.flux.shard.generator;

/**
 * Discord/Twitter Snowflake ID generator.
 * Format: | 42 bits: timestamp | 5 bits: worker | 5 bits: process | 12 bits: sequence |
 */
public class SnowflakeGenerator {
    
    // Discord epoch: 2015-01-01T00:00:00.000Z
    private static final long DISCORD_EPOCH = 1420070400000L;
    
    private final long workerId;
    private final long processId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;
    
    public SnowflakeGenerator(long workerId, long processId) {
        if (workerId < 0 || workerId >= 32) {
            throw new IllegalArgumentException("Worker ID must be 0-31");
        }
        if (processId < 0 || processId >= 32) {
            throw new IllegalArgumentException("Process ID must be 0-31");
        }
        this.workerId = workerId;
        this.processId = processId;
    }
    
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards!");
        }
        
        if (timestamp == lastTimestamp) {
            // Same millisecond - increment sequence
            sequence = (sequence + 1) & 0xFFF;
            if (sequence == 0) {
                // Sequence overflow - wait for next millisecond
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // New millisecond - reset sequence
            sequence = 0L;
        }
        
        lastTimestamp = timestamp;
        
        // Build the Snowflake ID
        long timeDiff = timestamp - DISCORD_EPOCH;
        return (timeDiff << 22) 
             | (workerId << 17) 
             | (processId << 12) 
             | sequence;
    }
    
    /**
     * Generate ID with specific timestamp offset (for testing temporal distribution)
     */
    public long generateWithOffset(long millisOffset) {
        long timestamp = System.currentTimeMillis() + millisOffset - DISCORD_EPOCH;
        long seq = (long) (Math.random() * 4096);
        return (timestamp << 22) | (workerId << 17) | (processId << 12) | seq;
    }
    
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
    
    /**
     * Extract timestamp from Snowflake ID
     */
    public static long extractTimestamp(long snowflakeId) {
        return (snowflakeId >> 22) + DISCORD_EPOCH;
    }
    
    /**
     * Extract worker ID from Snowflake ID
     */
    public static int extractWorkerId(long snowflakeId) {
        return (int) ((snowflakeId >> 17) & 0x1F);
    }
}
