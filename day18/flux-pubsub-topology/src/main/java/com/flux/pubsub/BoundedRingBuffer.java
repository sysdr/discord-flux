package com.flux.pubsub;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free bounded ring buffer for subscriber message queues.
 * Single producer (broker), single consumer (subscriber drain thread).
 */
public class BoundedRingBuffer {
    private final byte[][] buffer;
    private final int capacity;
    private final AtomicInteger writePos = new AtomicInteger(0);
    private final AtomicInteger readPos = new AtomicInteger(0);
    private final AtomicLong dropped = new AtomicLong(0);
    
    public BoundedRingBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new byte[capacity][];
    }
    
    /**
     * Attempt to add message to buffer.
     * @return true if added, false if full (caller should increment drop metric)
     */
    public boolean offer(byte[] data) {
        int write = writePos.get();
        int read = readPos.get();
        int nextWrite = (write + 1) % capacity;
        
        if (nextWrite == read) {
            dropped.incrementAndGet();
            return false; // Buffer full
        }
        
        buffer[write] = data;
        writePos.set(nextWrite);
        return true;
    }
    
    /**
     * Poll next message from buffer.
     * @return message or null if empty
     */
    public byte[] poll() {
        int read = readPos.get();
        int write = writePos.get();
        
        if (read == write) {
            return null; // Empty
        }
        
        byte[] data = buffer[read];
        buffer[read] = null; // Help GC
        readPos.set((read + 1) % capacity);
        return data;
    }
    
    public int size() {
        int write = writePos.get();
        int read = readPos.get();
        return write >= read ? write - read : capacity - read + write;
    }
    
    public long droppedCount() {
        return dropped.get();
    }
    
    public boolean isEmpty() {
        return readPos.get() == writePos.get();
    }
}
