package com.flux.gateway;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

public class BroadcastEngine {
    private final LocalConnectionRegistry registry;
    private final AtomicLong totalBroadcasts;
    private final AtomicLong totalRecipients;
    private final AtomicLong totalBytesSerialized;
    
    public BroadcastEngine(LocalConnectionRegistry registry) {
        this.registry = registry;
        this.totalBroadcasts = new AtomicLong(0);
        this.totalRecipients = new AtomicLong(0);
        this.totalBytesSerialized = new AtomicLong(0);
    }
    
    /**
     * Broadcast a message to all members of a guild.
     * Uses zero-copy serialization: serialize once, duplicate for each recipient.
     */
    public void fanOut(GuildId guildId, GuildMessage message) {
        long startTime = System.nanoTime();
        
        // Step 1: Find recipients
        Collection<GatewayConnection> recipients = registry.getGuildMembers(guildId);
        if (recipients.isEmpty()) {
            totalBroadcasts.incrementAndGet();
            System.out.println("[BROADCAST] No recipients for guild: " + guildId);
            return;
        }
        
        // Step 2: Serialize once into a direct ByteBuffer
        ByteBuffer sharedBuffer = serializeMessage(message);
        int messageSize = sharedBuffer.remaining();
        totalBytesSerialized.addAndGet(messageSize);
        
        // Step 3: Fan-out using buffer duplication
        int successCount = 0;
        int slowConsumerCount = 0;
        
        for (GatewayConnection conn : recipients) {
            ByteBuffer duplicate = sharedBuffer.duplicate(); // Same memory, independent position
            boolean queued = conn.queueWrite(duplicate);
            
            if (queued) {
                successCount++;
            } else {
                slowConsumerCount++;
            }
        }
        
        long latencyMicros = (System.nanoTime() - startTime) / 1000;
        
        totalBroadcasts.incrementAndGet();
        totalRecipients.addAndGet(successCount);
        
        System.out.printf("[BROADCAST] Guild=%s, Recipients=%d/%d, Latency=%dµs, Size=%dB%n",
            guildId, successCount, recipients.size(), latencyMicros, messageSize);
        
        if (slowConsumerCount > 0) {
            System.out.println("[BROADCAST] WARNING: " + slowConsumerCount + 
                               " slow consumers detected (write queue full)");
        }
    }
    
    /**
     * Serialize a GuildMessage into a direct ByteBuffer.
     * Format: [guildIdLen][guildId][userIdLen][userId][contentLen][content][timestamp]
     */
    private ByteBuffer serializeMessage(GuildMessage msg) {
        byte[] guildIdBytes = msg.guildId().getBytes(StandardCharsets.UTF_8);
        byte[] userIdBytes = msg.userId().getBytes(StandardCharsets.UTF_8);
        byte[] contentBytes = msg.content().getBytes(StandardCharsets.UTF_8);
        
        int totalSize = 4 + guildIdBytes.length +
                        4 + userIdBytes.length +
                        4 + contentBytes.length +
                        8; // timestamp (long)
        
        ByteBuffer buffer = ByteBuffer.allocateDirect(totalSize);
        
        buffer.putInt(guildIdBytes.length);
        buffer.put(guildIdBytes);
        
        buffer.putInt(userIdBytes.length);
        buffer.put(userIdBytes);
        
        buffer.putInt(contentBytes.length);
        buffer.put(contentBytes);
        
        buffer.putLong(msg.timestamp());
        
        buffer.flip(); // Prepare for reading
        return buffer;
    }
    
    public long getTotalBroadcasts() {
        return totalBroadcasts.get();
    }
    
    public long getTotalRecipients() {
        return totalRecipients.get();
    }
    
    public long getTotalBytesSerialized() {
        return totalBytesSerialized.get();
    }
}
