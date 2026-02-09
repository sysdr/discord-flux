package com.flux.storage;

import com.flux.model.Message;
import com.flux.model.SnowflakeId;

import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MemTable {
    private final ConcurrentSkipListMap<SnowflakeId, Message> data = new ConcurrentSkipListMap<>();
    private final AtomicLong sizeBytes = new AtomicLong(0);
    private final AtomicBoolean frozen = new AtomicBoolean(false);
    private static final long FLUSH_THRESHOLD = 16 * 1024 * 1024; // 16MB for faster demos
    
    public boolean put(Message message) {
        if (frozen.get()) {
            return false;
        }
        
        data.put(message.id(), message);
        sizeBytes.addAndGet(message.estimatedSizeBytes());
        return true;
    }
    
    public Message get(SnowflakeId id) {
        return data.get(id);
    }
    
    public boolean shouldFlush() {
        return sizeBytes.get() > FLUSH_THRESHOLD;
    }
    
    public void freeze() {
        frozen.set(true);
    }
    
    public boolean isFrozen() {
        return frozen.get();
    }
    
    public SortedMap<SnowflakeId, Message> getSnapshot() {
        return new ConcurrentSkipListMap<>(data);
    }
    
    public long sizeBytes() {
        return sizeBytes.get();
    }
    
    public int messageCount() {
        return data.size();
    }
    
    public void clear() {
        data.clear();
        sizeBytes.set(0);
        frozen.set(false);
    }
}
