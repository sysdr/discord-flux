package com.flux.persistence;

import com.flux.core.Message;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates LSM tree append-only writes using off-heap MemorySegment.
 */
public class LSMSimulator implements AutoCloseable {
    
    private static final long MEMTABLE_SIZE = 256 * 1024 * 1024; // 256MB
    private final Arena arena;
    private final MemorySegment memTable;
    private final AtomicLong writeOffset = new AtomicLong(0);
    private final AtomicLong writtenCount = new AtomicLong(0);
    private final AtomicLong flushCount = new AtomicLong(0);
    
    public LSMSimulator() {
        this.arena = Arena.ofShared();
        this.memTable = arena.allocate(MEMTABLE_SIZE, 8);
    }
    
    public void append(Message message) {
        var contentBytes = message.content().getBytes(StandardCharsets.UTF_8);
        var channelBytes = message.channelId().getBytes(StandardCharsets.UTF_8);
        
        // Record format: [id: 8][timestamp: 8][channel_len: 4][content_len: 4][channel_data][content_data]
        long recordSize = 8 + 8 + 4 + 4 + channelBytes.length + contentBytes.length;
        
        long offset = writeOffset.getAndAdd(recordSize);
        
        // Simulate memtable flush
        if (offset + recordSize > MEMTABLE_SIZE) {
            flush();
            offset = writeOffset.getAndAdd(recordSize);
        }
        
        try {
            memTable.set(ValueLayout.JAVA_LONG, offset, message.id());
            memTable.set(ValueLayout.JAVA_LONG, offset + 8, message.createdAt().toEpochMilli());
            memTable.set(ValueLayout.JAVA_INT, offset + 16, channelBytes.length);
            memTable.set(ValueLayout.JAVA_INT, offset + 20, contentBytes.length);
            
            MemorySegment.copy(channelBytes, 0, memTable, ValueLayout.JAVA_BYTE, offset + 24, channelBytes.length);
            MemorySegment.copy(contentBytes, 0, memTable, ValueLayout.JAVA_BYTE, 
                offset + 24 + channelBytes.length, contentBytes.length);
            
            writtenCount.incrementAndGet();
        } catch (Exception e) {
            // Ignore overflow for simulation
        }
    }
    
    private void flush() {
        // Simulate flushing memtable to SSTable (in reality would write to disk)
        flushCount.incrementAndGet();
        writeOffset.set(0);
    }
    
    public long getWrittenCount() {
        return writtenCount.get();
    }
    
    public long getFlushCount() {
        return flushCount.get();
    }
    
    public long getMemoryUsed() {
        return writeOffset.get();
    }
    
    public void reset() {
        writeOffset.set(0);
        writtenCount.set(0);
        flushCount.set(0);
    }
    
    @Override
    public void close() {
        arena.close();
    }
}
