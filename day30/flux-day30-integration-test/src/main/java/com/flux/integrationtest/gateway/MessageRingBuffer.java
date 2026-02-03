package com.flux.integrationtest.gateway;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Lock-free ring buffer for per-connection message queuing.
 * Implements backpressure: when buffer is full, marks connection as slow consumer.
 */
public class MessageRingBuffer {
    private static final int CAPACITY = 1024;
    private static final VarHandle WRITE_INDEX;
    private static final VarHandle READ_INDEX;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            WRITE_INDEX = lookup.findVarHandle(MessageRingBuffer.class, "writeIndex", long.class);
            READ_INDEX = lookup.findVarHandle(MessageRingBuffer.class, "readIndex", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private final Message[] buffer = new Message[CAPACITY];
    private volatile long writeIndex = 0;
    private volatile long readIndex = 0;
    
    /**
     * Try to enqueue message. Returns false if buffer is full (slow consumer).
     */
    public boolean offer(Message msg) {
        long currentWrite = (long) WRITE_INDEX.getAcquire(this);
        long currentRead = (long) READ_INDEX.getAcquire(this);
        
        if (currentWrite - currentRead >= CAPACITY) {
            return false; // Buffer full
        }
        
        buffer[(int)(currentWrite % CAPACITY)] = msg;
        WRITE_INDEX.setRelease(this, currentWrite + 1);
        return true;
    }
    
    /**
     * Try to dequeue message. Returns null if buffer is empty.
     */
    public Message poll() {
        long currentRead = (long) READ_INDEX.getAcquire(this);
        long currentWrite = (long) WRITE_INDEX.getAcquire(this);
        
        if (currentRead >= currentWrite) {
            return null; // Buffer empty
        }
        
        Message msg = buffer[(int)(currentRead % CAPACITY)];
        READ_INDEX.setRelease(this, currentRead + 1);
        return msg;
    }
    
    public int size() {
        long write = (long) WRITE_INDEX.getAcquire(this);
        long read = (long) READ_INDEX.getAcquire(this);
        return (int)(write - read);
    }
    
    public boolean isEmpty() {
        return size() == 0;
    }
    
    public boolean isFull() {
        return size() >= CAPACITY;
    }
}
