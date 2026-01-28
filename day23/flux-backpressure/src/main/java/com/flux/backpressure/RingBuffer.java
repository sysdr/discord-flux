package com.flux.backpressure;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;

/**
 * Lock-free ring buffer for outbound messages per connection.
 * Uses VarHandle for atomic head/tail operations without locks.
 * Power-of-2 capacity for fast modulo via bit masking.
 */
public class RingBuffer {
    private final ByteBuffer[] slots;
    private final int capacity;
    private final int mask; // capacity - 1 for fast modulo
    
    private static final VarHandle HEAD;
    private static final VarHandle TAIL;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            HEAD = lookup.findVarHandle(RingBuffer.class, "head", int.class);
            TAIL = lookup.findVarHandle(RingBuffer.class, "tail", int.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    @SuppressWarnings("unused")
    private volatile int head; // read position (consumer)
    @SuppressWarnings("unused")
    private volatile int tail; // write position (producer)
    
    public RingBuffer(int capacity) {
        if ((capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be power of 2");
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.slots = new ByteBuffer[capacity];
        
        // Pre-allocate all ByteBuffers (1KB each)
        for (int i = 0; i < capacity; i++) {
            slots[i] = ByteBuffer.allocate(1024);
        }
    }
    
    /**
     * Attempt to enqueue a message. Returns false if buffer is full.
     */
    public boolean offer(ByteBuffer message) {
        int currentTail = (int) TAIL.getOpaque(this);
        int nextTail = (currentTail + 1) & mask;
        int currentHead = (int) HEAD.getOpaque(this);
        
        if (nextTail == currentHead) {
            return false; // buffer full
        }
        
        // Copy message into slot
        ByteBuffer slot = slots[currentTail];
        slot.clear();
        slot.put(message.duplicate());
        slot.flip();
        
        TAIL.setRelease(this, nextTail);
        return true;
    }
    
    /**
     * Dequeue the next message. Returns null if buffer is empty.
     */
    public ByteBuffer poll() {
        int currentHead = (int) HEAD.getOpaque(this);
        int currentTail = (int) TAIL.getOpaque(this);
        
        if (currentHead == currentTail) {
            return null; // buffer empty
        }
        
        ByteBuffer result = slots[currentHead];
        HEAD.setRelease(this, (currentHead + 1) & mask);
        return result;
    }
    
    /**
     * Peek at head without removing.
     */
    public ByteBuffer peek() {
        int currentHead = (int) HEAD.getOpaque(this);
        int currentTail = (int) TAIL.getOpaque(this);
        
        if (currentHead == currentTail) {
            return null;
        }
        
        return slots[currentHead];
    }
    
    /**
     * Check if buffer is empty.
     */
    public boolean isEmpty() {
        int currentHead = (int) HEAD.getOpaque(this);
        int currentTail = (int) TAIL.getOpaque(this);
        return currentHead == currentTail;
    }
    
    /**
     * Get current buffer depth (number of pending messages).
     */
    public int size() {
        int currentHead = (int) HEAD.getOpaque(this);
        int currentTail = (int) TAIL.getOpaque(this);
        return (currentTail - currentHead) & mask;
    }
    
    /**
     * Get buffer utilization percentage (0-100).
     */
    public int getUtilization() {
        return (size() * 100) / capacity;
    }
    
    public int getCapacity() {
        return capacity;
    }
}
