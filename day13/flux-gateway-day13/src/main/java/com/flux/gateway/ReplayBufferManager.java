package com.flux.gateway;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ReplayBufferManager {
    private final ConcurrentHashMap<String, ReplayBuffer> buffers;
    private final int bufferCapacity;
    private final long idleTimeoutMs;
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicLong totalMessagesBuffered = new AtomicLong(0);
    private final AtomicLong totalReplays = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);
    
    public ReplayBufferManager(int bufferCapacity, long idleTimeoutMs) {
        this.buffers = new ConcurrentHashMap<>();
        this.bufferCapacity = bufferCapacity;
        this.idleTimeoutMs = idleTimeoutMs;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        
        // Schedule cleanup every minute
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }
    
    public long bufferMessage(String userId, byte[] message) {
        ReplayBuffer buffer = buffers.computeIfAbsent(userId, k -> new ReplayBuffer(bufferCapacity));
        long sequence = buffer.write(message);
        totalMessagesBuffered.incrementAndGet();
        return sequence;
    }
    
    public List<byte[]> replay(String userId, long fromSequence) {
        ReplayBuffer buffer = buffers.get(userId);
        if (buffer != null) {
            totalReplays.incrementAndGet();
            return buffer.readFrom(fromSequence);
        }
        return List.of();
    }
    
    public ReplayBuffer getBuffer(String userId) {
        return buffers.get(userId);
    }
    
    public void removeBuffer(String userId) {
        buffers.remove(userId);
    }
    
    private void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        
        buffers.entrySet().removeIf(entry -> {
            long lastActivity = entry.getValue().getLastWriteTime();
            if ((now - lastActivity) > idleTimeoutMs) {
                return true;
            }
            return false;
        });
        
        evictionCount.addAndGet(removed);
    }
    
    public int getActiveBufferCount() {
        return buffers.size();
    }
    
    public long getTotalMessagesBuffered() {
        return totalMessagesBuffered.get();
    }
    
    public long getTotalReplays() {
        return totalReplays.get();
    }
    
    public long getEvictionCount() {
        return evictionCount.get();
    }
    
    public void shutdown() {
        cleanupExecutor.shutdown();
    }
}
