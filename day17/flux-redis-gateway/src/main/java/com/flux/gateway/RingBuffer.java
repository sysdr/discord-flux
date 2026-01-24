package com.flux.gateway;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Lock-free ring buffer for backpressure handling.
 * Uses VarHandle for atomic operations on head/tail pointers.
 */
public class RingBuffer {
    private final String[] slots;
    private final int mask;
    
    private volatile long head = 0; // Consumer position
    private volatile long tail = 0; // Producer position
    
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
    
    public RingBuffer(int capacity) {
        if ((capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be power of 2");
        }
        this.slots = new String[capacity];
        this.mask = capacity - 1;
    }
    
    /**
     * Attempt to add message to buffer.
     * @return true if added, false if buffer full (backpressure)
     */
    public boolean offer(String message) {
        long currentTail = (long) TAIL.getAcquire(this);
        long currentHead = (long) HEAD.getAcquire(this);
        
        // Check if buffer is full
        if (currentTail - currentHead >= slots.length) {
            return false; // Backpressure: drop message
        }
        
        int index = (int) (currentTail & mask);
        slots[index] = message;
        
        // Memory barrier: ensure write completes before updating tail
        TAIL.setRelease(this, currentTail + 1);
        return true;
    }
    
    /**
     * Poll next message from buffer.
     * @return message or null if empty
     */
    public String poll() {
        long currentHead = (long) HEAD.getAcquire(this);
        long currentTail = (long) TAIL.getAcquire(this);
        
        if (currentHead >= currentTail) {
            return null; // Buffer empty
        }
        
        int index = (int) (currentHead & mask);
        String message = slots[index];
        slots[index] = null; // Clear for GC
        
        HEAD.setRelease(this, currentHead + 1);
        return message;
    }
    
    /**
     * Get current buffer utilization (0.0 to 1.0)
     */
    public double utilization() {
        long currentHead = (long) HEAD.getAcquire(this);
        long currentTail = (long) TAIL.getAcquire(this);
        long size = currentTail - currentHead;
        return (double) size / slots.length;
    }
    
    public int size() {
        long currentHead = (long) HEAD.getAcquire(this);
        long currentTail = (long) TAIL.getAcquire(this);
        return (int) (currentTail - currentHead);
    }
    
    public int capacity() {
        return slots.length;
    }
}
