package com.flux.typing;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicLong;

public class TypingEventRing {
    private static final int DEFAULT_RING_SIZE = 16384; // 16K entries
    private static final int ENTRY_SIZE = 24; // 8 bytes each: timestamp, userId, channelId
    private static final long TTL_NANOS = 5_000_000_000L; // 5 seconds
    
    private final MemorySegment ring;
    private final AtomicLong writeHead;
    private final int ringSize;
    
    public TypingEventRing() {
        this(DEFAULT_RING_SIZE);
    }
    
    public TypingEventRing(int ringSize) {
        this.ringSize = ringSize;
        this.ring = Arena.ofShared().allocate((long) ringSize * ENTRY_SIZE, 64); // 64-byte alignment
        this.writeHead = new AtomicLong(0);
    }
    
    public void publish(long userId, long channelId) {
        long timestamp = System.nanoTime();
        long index = writeHead.getAndIncrement();
        int slot = (int) (index % ringSize);
        long offset = (long) slot * ENTRY_SIZE;
        
        ring.set(ValueLayout.JAVA_LONG, offset, timestamp);
        ring.set(ValueLayout.JAVA_LONG, offset + 8, userId);
        ring.set(ValueLayout.JAVA_LONG, offset + 16, channelId);
    }
    
    public void collectActiveTypers(long channelId, long[] output, int[] outCount) {
        long cutoffTime = System.nanoTime() - TTL_NANOS;
        long head = writeHead.get();
        int count = 0;
        
        // Scan the ring from oldest potentially valid entry
        long startIdx = Math.max(0, head - ringSize);
        
        for (long i = startIdx; i < head && count < output.length; i++) {
            int slot = (int) (i % ringSize);
            long offset = (long) slot * ENTRY_SIZE;
            
            long timestamp = ring.get(ValueLayout.JAVA_LONG, offset);
            if (timestamp < cutoffTime) continue; // Expired
            
            long storedChannelId = ring.get(ValueLayout.JAVA_LONG, offset + 16);
            if (storedChannelId != channelId) continue; // Different channel
            
            long userId = ring.get(ValueLayout.JAVA_LONG, offset + 8);
            
            // Check for duplicates (same user already in output)
            boolean isDuplicate = false;
            for (int j = 0; j < count; j++) {
                if (output[j] == userId) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (!isDuplicate) {
                output[count++] = userId;
            }
        }
        
        outCount[0] = count;
    }
    
    public long getTotalEvents() {
        return writeHead.get();
    }
    
    public int getRingSize() {
        return ringSize;
    }
    
    public double getSaturation() {
        long eventsPerSecond = writeHead.get() / Math.max(1, System.nanoTime() / 1_000_000_000L);
        return (double) eventsPerSecond / ringSize;
    }
}
