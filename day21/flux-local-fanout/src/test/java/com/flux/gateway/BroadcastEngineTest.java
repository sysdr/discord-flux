package com.flux.gateway;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BroadcastEngineTest {
    
    @Test
    void testBroadcastMetrics() {
        LocalConnectionRegistry registry = new LocalConnectionRegistry();
        BroadcastEngine engine = new BroadcastEngine(registry);
        
        // Create 3 connections subscribed to guild_001
        GuildId guild = new GuildId("guild_001");
        
        for (int i = 0; i < 3; i++) {
            SessionId sessionId = SessionId.generate();
            GatewayConnection conn = new GatewayConnection(sessionId, null, null);
            conn.subscribeToGuild(guild);
            registry.register(sessionId, conn);
        }
        
        // Broadcast a message
        GuildMessage msg = new GuildMessage("guild_001", "user_123", "Test message");
        engine.fanOut(guild, msg);
        
        // Verify metrics
        assertEquals(1, engine.getTotalBroadcasts());
        assertEquals(3, engine.getTotalRecipients());
        assertTrue(engine.getTotalBytesSerialized() > 0);
    }
    
    @Test
    void testEmptyGuildBroadcast() {
        LocalConnectionRegistry registry = new LocalConnectionRegistry();
        BroadcastEngine engine = new BroadcastEngine(registry);
        
        GuildId guild = new GuildId("empty_guild");
        GuildMessage msg = new GuildMessage("empty_guild", "user_123", "Test");
        
        engine.fanOut(guild, msg);
        
        // Should not crash, metrics should show 0 recipients
        assertEquals(1, engine.getTotalBroadcasts());
        assertEquals(0, engine.getTotalRecipients());
    }
}
