package com.flux.presence;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free MPSC ring buffer for per-connection message buffering.
 * Multiple Virtual Thread producers can offer messages without blocking.
 * Single consumer (connection writer thread) polls messages.
 * 
 * Backpressure strategy: Drop oldest messages when full (graceful degradation).
 */
public class ConnectionRingBuffer {
    private final ByteBuffer[] ring;
    private final int capacity;
    private final int mask; // capacity - 1, for fast modulo
    
    private volatile int writeIndex = 0;
    private volatile int readIndex = 0;
    
    private final AtomicLong offeredCount = new AtomicLong(0);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final AtomicLong consumedCount = new AtomicLong(0);
    
    private static final VarHandle WRITE_INDEX;
    private static final VarHandle READ_INDEX;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            WRITE_INDEX = lookup.findVarHandle(ConnectionRingBuffer.class, "writeIndex", int.class);
            READ_INDEX = lookup.findVarHandle(ConnectionRingBuffer.class, "readIndex", int.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    public ConnectionRingBuffer(int capacity) {
        if ((capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be power of 2");
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.ring = new ByteBuffer[capacity];
    }
    
    /**
     * Offer a message to the ring buffer (multi-producer safe).
     * If buffer is full, drops the oldest message.
     */
    public boolean offer(ByteBuffer message) {
        offeredCount.incrementAndGet();
        
        int current, next;
        do {
            current = (int) WRITE_INDEX.getVolatile(this);
            next = (current + 1) & mask;
            
            // Check if ring is full
            int read = (int) READ_INDEX.getVolatile(this);
            if (next == read) {
                // Ring full - advance read pointer to drop oldest
                READ_INDEX.compareAndSet(this, read, (read + 1) & mask);
                droppedCount.incrementAndGet();
            }
        } while (!WRITE_INDEX.compareAndSet(this, current, next));
        
        // Store message at the slot we just claimed
        ring[next] = message;
        return true;
    }
    
    /**
     * Poll a message from the ring buffer (single consumer).
     */
    public ByteBuffer poll() {
        int read = readIndex;
        int write = (int) WRITE_INDEX.getVolatile(this);
        
        if (read == write) {
            return null; // Empty
        }
        
        int next = (read + 1) & mask;
        ByteBuffer message = ring[next];
        ring[next] = null; // Help GC
        
        READ_INDEX.setVolatile(this, next);
        consumedCount.incrementAndGet();
        
        return message;
    }
    
    public int size() {
        int write = (int) WRITE_INDEX.getVolatile(this);
        int read = (int) READ_INDEX.getVolatile(this);
        return (write - read) & mask;
    }
    
    public boolean isEmpty() {
        return readIndex == (int) WRITE_INDEX.getVolatile(this);
    }
    
    public long getOfferedCount() { return offeredCount.get(); }
    public long getDroppedCount() { return droppedCount.get(); }
    public long getConsumedCount() { return consumedCount.get(); }
    public double getDropRate() {
        long offered = offeredCount.get();
        return offered > 0 ? (double) droppedCount.get() / offered : 0.0;
    }
}
