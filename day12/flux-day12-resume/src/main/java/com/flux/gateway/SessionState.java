package com.flux.gateway;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicLong;

public class SessionState {
    private final String sessionId;
    private final AtomicLong sequence;
    private final ByteBuffer[] ringBuffer;
    private final int ringSize;
    private volatile int ringHead;
    private volatile long ringStartSeq;
    private volatile State state;
    private volatile SocketChannel channel;
    private volatile long lastActivity;
    private volatile long disconnectTime;
    
    private static final VarHandle STATE;
    private static final VarHandle RING_HEAD;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            STATE = lookup.findVarHandle(SessionState.class, "state", State.class);
            RING_HEAD = lookup.findVarHandle(SessionState.class, "ringHead", int.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    public enum State {
        ACTIVE,
        DISCONNECTED,
        EXPIRED
    }
    
    public SessionState(String sessionId, SocketChannel channel, int ringSize) {
        this.sessionId = sessionId;
        this.channel = channel;
        this.ringSize = ringSize;
        this.sequence = new AtomicLong(0);
        this.ringBuffer = new ByteBuffer[ringSize];
        this.ringHead = 0;
        this.ringStartSeq = 0;
        this.state = State.ACTIVE;
        this.lastActivity = System.currentTimeMillis();
        
        // Allocate direct buffers for off-heap storage
        for (int i = 0; i < ringSize; i++) {
            ringBuffer[i] = ByteBuffer.allocateDirect(1024); // 1KB per slot
        }
    }
    
    // Store message in ring buffer
    public void storeMessage(Message message) {
        long seq = message.seq();
        int index = (int)(seq % ringSize);
        
        ByteBuffer slot = ringBuffer[index];
        ByteBuffer serialized = message.serialize();
        
        synchronized (slot) {
            slot.clear();
            slot.put(serialized);
            slot.flip();
        }
        
        // Update sequence counter to match the message sequence
        long currentSeq = sequence.get();
        while (currentSeq <= seq && !sequence.compareAndSet(currentSeq, seq + 1)) {
            currentSeq = sequence.get();
        }
        
        // Update ring start sequence if we wrapped around
        if (seq >= ringStartSeq + ringSize) {
            ringStartSeq = seq - ringSize + 1;
        }
        
        lastActivity = System.currentTimeMillis();
    }
    
    // Get next sequence number atomically
    public long nextSequence() {
        return sequence.getAndIncrement();
    }
    
    // Get current sequence (for resume)
    public long currentSequence() {
        return sequence.get();
    }
    
    // Retrieve messages from sequence onwards
    public Message[] getMessagesSince(long fromSeq) {
        long currentSeq = currentSequence();
        
        // Check if requested sequence is too old (wrapped out of buffer)
        if (fromSeq < ringStartSeq) {
            fromSeq = ringStartSeq;
        }
        
        int count = (int)(currentSeq - fromSeq);
        if (count <= 0) {
            return new Message[0];
        }
        
        Message[] messages = new Message[Math.min(count, ringSize)];
        int retrieved = 0;
        
        for (long seq = fromSeq; seq < currentSeq && retrieved < messages.length; seq++) {
            int index = (int)(seq % ringSize);
            ByteBuffer slot = ringBuffer[index];
            
            synchronized (slot) {
                if (slot.position() == 0 && slot.limit() == 0) {
                    continue; // Empty slot
                }
                
                ByteBuffer copy = ByteBuffer.allocate(slot.limit());
                slot.position(0);
                copy.put(slot);
                copy.flip();
                
                messages[retrieved++] = Message.deserialize(copy);
            }
        }
        
        if (retrieved < messages.length) {
            Message[] trimmed = new Message[retrieved];
            System.arraycopy(messages, 0, trimmed, 0, retrieved);
            return trimmed;
        }
        
        return messages;
    }
    
    // Transition to DISCONNECTED state
    public boolean disconnect() {
        boolean transitioned = STATE.compareAndSet(this, State.ACTIVE, State.DISCONNECTED);
        if (transitioned) {
            disconnectTime = System.currentTimeMillis();
            channel = null; // Release channel reference
        }
        return transitioned;
    }
    
    // Attempt to resume with new channel
    public boolean resume(SocketChannel newChannel) {
        boolean transitioned = STATE.compareAndSet(this, State.DISCONNECTED, State.ACTIVE);
        if (transitioned) {
            this.channel = newChannel;
            this.lastActivity = System.currentTimeMillis();
        }
        return transitioned;
    }
    
    // Mark session as expired
    public boolean expire() {
        return STATE.compareAndSet(this, State.DISCONNECTED, State.EXPIRED);
    }
    
    // Cleanup resources
    public void cleanup() {
        state = State.EXPIRED;
        channel = null;
        // DirectBuffers are cleaned up by GC, but we can help by clearing references
        for (int i = 0; i < ringSize; i++) {
            ringBuffer[i] = null;
        }
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public State getState() { return state; }
    public SocketChannel getChannel() { return channel; }
    public long getLastActivity() { return lastActivity; }
    public long getDisconnectTime() { return disconnectTime; }
    public int getRingSize() { return ringSize; }
}
