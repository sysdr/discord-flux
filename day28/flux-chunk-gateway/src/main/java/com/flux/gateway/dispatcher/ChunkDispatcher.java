package com.flux.gateway.dispatcher;

import com.flux.gateway.protocol.ChunkRequest;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free ring buffer for chunk requests.
 * Prevents slow Redis queries from blocking new requests.
 */
public class ChunkDispatcher {
    private static final int BUFFER_SIZE = 8192; // Must be power of 2
    private final ChunkRequest[] buffer = new ChunkRequest[BUFFER_SIZE];
    
    private static final VarHandle WRITE_SEQ;
    private static final VarHandle READ_SEQ;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            WRITE_SEQ = lookup.findVarHandle(ChunkDispatcher.class, "writeSequence", long.class);
            READ_SEQ = lookup.findVarHandle(ChunkDispatcher.class, "readSequence", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private volatile long writeSequence = 0L;
    private volatile long readSequence = 0L;
    
    private final AtomicLong enqueueCount = new AtomicLong(0);
    private final AtomicLong rejectCount = new AtomicLong(0);
    
    /**
     * Enqueue a chunk request. Returns false if buffer is full (backpressure).
     */
    public boolean enqueue(ChunkRequest request) {
        long current = writeSequence;
        long next = current + 1;
        int index = (int)(current & (BUFFER_SIZE - 1));
        
        // Check capacity (prevent overwriting unread data)
        if (next - readSequence >= BUFFER_SIZE) {
            rejectCount.incrementAndGet();
            return false; // FULL - apply backpressure
        }
        
        buffer[index] = request;
        
        // CAS-based publish (ensures visibility)
        boolean success = (boolean) WRITE_SEQ.compareAndSet(this, current, next);
        if (success) {
            enqueueCount.incrementAndGet();
        }
        return success;
    }
    
    /**
     * Dequeue next chunk request. Returns null if empty.
     */
    public ChunkRequest dequeue() {
        long current = readSequence;
        
        // Check if empty
        if (current >= writeSequence) {
            return null;
        }
        
        int index = (int)(current & (BUFFER_SIZE - 1));
        ChunkRequest request = buffer[index];
        buffer[index] = null; // Allow GC
        
        // Advance read pointer
        READ_SEQ.compareAndSet(this, current, current + 1);
        return request;
    }
    
    /**
     * Calculate current utilization (0.0 to 1.0).
     */
    public double getUtilization() {
        long pending = writeSequence - readSequence;
        return (double) pending / BUFFER_SIZE;
    }
    
    public long getEnqueueCount() {
        return enqueueCount.get();
    }
    
    public long getRejectCount() {
        return rejectCount.get();
    }
}
