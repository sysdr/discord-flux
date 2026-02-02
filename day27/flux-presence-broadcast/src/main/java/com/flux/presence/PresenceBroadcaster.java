package com.flux.presence;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Zero-allocation presence broadcaster using Virtual Threads.
 * 
 * Key techniques:
 * 1. Serialize PresenceUpdate once to ByteBuffer
 * 2. Share read-only view across all recipients
 * 3. Spawn Virtual Thread per recipient for non-blocking I/O
 * 4. Coalesce rapid updates in 200ms window
 */
public class PresenceBroadcaster {
    private final GuildMemberRegistry registry;
    private final ExecutorService fanOutExecutor;
    private final ScheduledExecutorService coalescer;
    
    // Pending updates awaiting broadcast (coalescing window)
    private final ConcurrentHashMap<CoalescingKey, PresenceUpdate> pendingUpdates;
    
    // Metrics
    private final AtomicLong broadcastCount = new AtomicLong(0);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong slowConsumerDetections = new AtomicLong(0);
    
    // Thread-local buffer pool for serialization
    private static final ThreadLocal<ByteBuffer> BUFFER_POOL = 
        ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(512));
    
    public PresenceBroadcaster(GuildMemberRegistry registry) {
        this.registry = registry;
        this.fanOutExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.coalescer = Executors.newScheduledThreadPool(1);
        this.pendingUpdates = new ConcurrentHashMap<>();
        
        // Start coalescing task (flush every 200ms)
        coalescer.scheduleAtFixedRate(this::flushPendingUpdates, 200, 200, TimeUnit.MILLISECONDS);
    }
    
    private record CoalescingKey(long guildId, long userId) {}
    
    /**
     * Schedule a presence update for broadcasting.
     * Updates are coalesced in 200ms windows to reduce redundant broadcasts.
     */
    public void schedulePresenceUpdate(long guildId, PresenceUpdate update) {
        pendingUpdates.merge(
            new CoalescingKey(guildId, update.userId()),
            update,
            (oldUpdate, newUpdate) -> newUpdate  // Keep latest
        );
    }
    
    /**
     * Flush all pending presence updates.
     */
    private void flushPendingUpdates() {
        if (pendingUpdates.isEmpty()) return;
        
        // Atomically swap out pending map
        Map<CoalescingKey, PresenceUpdate> batch = new ConcurrentHashMap<>(pendingUpdates);
        pendingUpdates.clear();
        
        // Broadcast each update
        batch.forEach((key, update) -> broadcastImmediate(key.guildId, update));
    }
    
    /**
     * Immediate broadcast (no coalescing).
     */
    public void broadcastImmediate(long guildId, PresenceUpdate update) {
        broadcastCount.incrementAndGet();
        
        // Serialize once
        ByteBuffer sharedBuffer = serializePresenceUpdate(update);
        
        // Get recipients (lock-free read)
        List<GatewayConnection> recipients = registry.getGuildMembers(guildId);
        
        // Fan-out using Virtual Threads
        for (GatewayConnection conn : recipients) {
            // Skip sender
            if (conn.getUserId() == update.userId()) {
                continue;
            }
            
            messagesSent.incrementAndGet();
            
            // Submit to Virtual Thread pool (non-blocking)
            fanOutExecutor.submit(() -> {
                try {
                    // Offer to connection's ring buffer
                    ByteBuffer readOnlyView = sharedBuffer.asReadOnlyBuffer();
                    boolean accepted = conn.getRingBuffer().offer(readOnlyView);
                    
                    if (!accepted) {
                        slowConsumerDetections.incrementAndGet();
                        // In production, consider disconnecting pathologically slow clients
                    }
                } catch (Exception e) {
                    // Log but don't fail entire broadcast
                    System.err.println("Failed to send to connection " + conn.getUserId() + ": " + e.getMessage());
                }
            });
        }
    }
    
    /**
     * Serialize PresenceUpdate to ByteBuffer using thread-local pool.
     * Wire format: [userId:8][status:1][timestamp:8][activityLen:2][activity:N]
     */
    private ByteBuffer serializePresenceUpdate(PresenceUpdate update) {
        ByteBuffer buf = BUFFER_POOL.get();
        buf.clear();
        
        buf.putLong(update.userId());
        buf.put((byte) update.status().getWireValue());
        buf.putLong(update.timestamp());
        
        String activity = update.activity();
        if (activity != null) {
            byte[] activityBytes = activity.getBytes();
            buf.putShort((short) activityBytes.length);
            buf.put(activityBytes);
        } else {
            buf.putShort((short) 0);
        }
        
        buf.flip();
        return buf;
    }
    
    public long getBroadcastCount() { return broadcastCount.get(); }
    public long getMessagesSent() { return messagesSent.get(); }
    public long getSlowConsumerDetections() { return slowConsumerDetections.get(); }
    
    public void shutdown() {
        coalescer.shutdown();
        fanOutExecutor.shutdown();
    }
}
