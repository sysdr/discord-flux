package com.flux.ringbuffer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Lock-free bounded ring buffer for handling slow consumers.
 * Uses VarHandle atomic operations for concurrent access without locks.
 */
public final class RingBuffer {
    private final BufferSlot[] buffer;
    private final int capacity;
    private final String clientId;
    
    private static final VarHandle HEAD;
    private static final VarHandle TAIL;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            HEAD = lookup.findVarHandle(RingBuffer.class, "head", long.class);
            TAIL = lookup.findVarHandle(RingBuffer.class, "tail", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private volatile long head = 0;  // Write position
    private volatile long tail = 0;  // Read position
    private volatile long droppedCount = 0;
    
    public RingBuffer(int capacity, String clientId) {
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be power of 2");
        }
        this.capacity = capacity;
        this.clientId = clientId;
        this.buffer = new BufferSlot[capacity];
        
        // Pre-allocate all slots (zero allocation on hot path)
        for (int i = 0; i < capacity; i++) {
            buffer[i] = new BufferSlot();
        }
    }
    
    /**
     * Attempt to write an event to the buffer.
     * Returns false if buffer is full (backpressure signal).
     */
    public boolean tryWrite(GuildEvent event) {
        long currentHead = (long) HEAD.getOpaque(this);
        long currentTail = (long) TAIL.getAcquire(this);
        
        // Check if buffer is full
        if (currentHead - currentTail >= capacity) {
            droppedCount++;
            return false;  // Backpressure!
        }
        
        int writeIndex = (int) (currentHead & (capacity - 1));  // Fast modulo
        buffer[writeIndex].update(event.eventId(), event.payload(), System.nanoTime());
        
        // Publish write with release semantics
        HEAD.setRelease(this, currentHead + 1);
        return true;
    }
    
    /**
     * Read the next event from the buffer.
     * Returns null if buffer is empty.
     */
    public BufferSlot tryRead() {
        long currentTail = (long) TAIL.getOpaque(this);
        long currentHead = (long) HEAD.getAcquire(this);
        
        if (currentTail >= currentHead) {
            return null;  // Empty
        }
        
        int readIndex = (int) (currentTail & (capacity - 1));
        BufferSlot slot = buffer[readIndex];
        
        // Advance tail
        TAIL.setRelease(this, currentTail + 1);
        return slot;
    }
    
    /**
     * Calculate current buffer utilization percentage.
     */
    public int utilizationPercent() {
        long currentHead = (long) HEAD.getOpaque(this);
        long currentTail = (long) TAIL.getOpaque(this);
        long size = currentHead - currentTail;
        return (int) ((size * 100) / capacity);
    }
    
    public long size() {
        long currentHead = (long) HEAD.getOpaque(this);
        long currentTail = (long) TAIL.getOpaque(this);
        return currentHead - currentTail;
    }
    
    public long getDroppedCount() {
        return droppedCount;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public int getCapacity() {
        return capacity;
    }
}

/**
 * Reusable buffer slot to avoid allocation on hot path.
 */
class BufferSlot {
    private long eventId;
    private String payload;
    private long enqueuedAt;
    
    void update(long eventId, String payload, long enqueuedAt) {
        this.eventId = eventId;
        this.payload = payload;
        this.enqueuedAt = enqueuedAt;
    }
    
    public long eventId() { return eventId; }
    public String payload() { return payload; }
    public long enqueuedAt() { return enqueuedAt; }
    
    public long ageNanos() {
        return System.nanoTime() - enqueuedAt;
    }
}
