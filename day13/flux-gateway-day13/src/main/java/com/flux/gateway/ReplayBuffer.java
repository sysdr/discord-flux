package com.flux.gateway;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ReplayBuffer {
    private static final int MAX_MESSAGE_SIZE = 4096;
    private static final VarHandle HEAD;
    private static final VarHandle TAIL;
    private static final VarHandle SEQUENCE;
    
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            HEAD = l.findVarHandle(ReplayBuffer.class, "head", int.class);
            TAIL = l.findVarHandle(ReplayBuffer.class, "tail", int.class);
            SEQUENCE = l.findVarHandle(ReplayBuffer.class, "sequence", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private final ByteBuffer[] slots;
    private final long[] sequences;
    private final int capacity;
    private volatile int head = 0;
    private volatile int tail = 0;
    private volatile long sequence = 0;
    private volatile long lastWriteTime = System.currentTimeMillis();
    
    public ReplayBuffer(int capacity) {
        this.capacity = capacity;
        this.slots = new ByteBuffer[capacity];
        this.sequences = new long[capacity];
        
        for (int i = 0; i < capacity; i++) {
            slots[i] = ByteBuffer.allocateDirect(MAX_MESSAGE_SIZE);
        }
    }
    
    public long write(byte[] message) {
        if (message.length > MAX_MESSAGE_SIZE) {
            throw new IllegalArgumentException("Message too large: " + message.length);
        }
        
        int currentHead = (int) HEAD.getAndAdd(this, 1);
        int index = currentHead % capacity;
        long currentSeq = (long) SEQUENCE.getAndAdd(this, 1);
        
        ByteBuffer buffer = slots[index];
        buffer.clear();
        buffer.put(message);
        buffer.flip();
        
        sequences[index] = currentSeq;
        
        // Update tail if we're overwriting
        int size = size();
        if (size >= capacity) {
            TAIL.setOpaque(this, currentHead - capacity + 1);
        }
        
        lastWriteTime = System.currentTimeMillis();
        return currentSeq;
    }
    
    public List<byte[]> readFrom(long fromSequence) {
        List<byte[]> messages = new ArrayList<>();
        int currentHead = (int) HEAD.getOpaque(this);
        int currentTail = (int) TAIL.getOpaque(this);
        
        if (currentHead == currentTail) {
            return messages; // Empty buffer
        }
        
        for (int i = currentTail; i < currentHead; i++) {
            int index = i % capacity;
            long seq = sequences[index];
            
            if (seq > fromSequence) {
                ByteBuffer buffer = slots[index];
                byte[] message = new byte[buffer.remaining()];
                buffer.duplicate().get(message);
                messages.add(message);
            }
        }
        
        return messages;
    }
    
    public int size() {
        int h = (int) HEAD.getOpaque(this);
        int t = (int) TAIL.getOpaque(this);
        return h - t;
    }
    
    public long getCurrentSequence() {
        return (long) SEQUENCE.getOpaque(this);
    }
    
    public long getLastWriteTime() {
        return lastWriteTime;
    }
    
    public int getCapacity() {
        return capacity;
    }
    
    public void clear() {
        HEAD.setOpaque(this, 0);
        TAIL.setOpaque(this, 0);
        SEQUENCE.setOpaque(this, 0);
    }
}
