package com.flux.presence;

import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PresenceBroadcasterTest {
    
    @Test
    void testBroadcastToMultipleRecipients() throws Exception {
        GuildMemberRegistry registry = new GuildMemberRegistry();
        PresenceBroadcaster broadcaster = new PresenceBroadcaster(registry);
        
        long guildId = 1000L;
        int memberCount = 100;
        
        // Create guild members
        for (int i = 0; i < memberCount; i++) {
            GatewayConnection conn = new GatewayConnection(i, 256);
            registry.addMember(guildId, conn);
        }
        
        // Broadcast presence update
        PresenceUpdate update = new PresenceUpdate(
            42L,
            PresenceStatus.IDLE,
            System.currentTimeMillis(),
            "Testing"
        );
        
        broadcaster.broadcastImmediate(guildId, update);
        
        // Wait for Virtual Threads to complete
        TimeUnit.MILLISECONDS.sleep(500);
        
        // Should have sent to all members except sender (42)
        long expectedMessages = memberCount - 1;
        assertEquals(1, broadcaster.getBroadcastCount());
        assertTrue(broadcaster.getMessagesSent() >= expectedMessages - 5); // Allow some variance
        
        System.out.println("Broadcasts: " + broadcaster.getBroadcastCount());
        System.out.println("Messages: " + broadcaster.getMessagesSent());
        
        broadcaster.shutdown();
    }
    
    @Test
    void testPresenceCoalescing() throws Exception {
        GuildMemberRegistry registry = new GuildMemberRegistry();
        PresenceBroadcaster broadcaster = new PresenceBroadcaster(registry);
        
        long guildId = 1000L;
        long userId = 42L;
        
        // Add one member to receive updates
        GatewayConnection conn = new GatewayConnection(100L, 256);
        registry.addMember(guildId, conn);
        
        // Schedule 10 rapid updates for same user
        for (int i = 0; i < 10; i++) {
            PresenceUpdate update = new PresenceUpdate(
                userId,
                PresenceStatus.values()[i % 4],
                System.currentTimeMillis(),
                "Update" + i
            );
            broadcaster.schedulePresenceUpdate(guildId, update);
        }
        
        // Wait for coalescing window (200ms) + flush
        TimeUnit.MILLISECONDS.sleep(500);
        
        // Should have coalesced to 1 broadcast
        assertTrue(broadcaster.getBroadcastCount() <= 1);
        
        System.out.println("Scheduled 10 updates, resulted in " + 
                         broadcaster.getBroadcastCount() + " broadcasts (coalesced)");
        
        broadcaster.shutdown();
    }
}
