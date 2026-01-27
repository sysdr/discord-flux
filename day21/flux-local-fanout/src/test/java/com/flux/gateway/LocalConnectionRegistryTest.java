package com.flux.gateway;

import org.junit.jupiter.api.Test;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import static org.junit.jupiter.api.Assertions.*;

class LocalConnectionRegistryTest {
    
    @Test
    void testRegisterAndUnregister() {
        LocalConnectionRegistry registry = new LocalConnectionRegistry();
        SessionId sessionId = SessionId.generate();
        
        // Mock connection (in real tests, use proper mocks)
        GatewayConnection conn = new GatewayConnection(sessionId, null, null);
        
        registry.register(sessionId, conn);
        assertEquals(1, registry.size());
        
        registry.unregister(sessionId);
        assertEquals(0, registry.size());
    }
    
    @Test
    void testDuplicateRegistrationThrows() {
        LocalConnectionRegistry registry = new LocalConnectionRegistry();
        SessionId sessionId = SessionId.generate();
        
        GatewayConnection conn1 = new GatewayConnection(sessionId, null, null);
        GatewayConnection conn2 = new GatewayConnection(sessionId, null, null);
        
        registry.register(sessionId, conn1);
        
        assertThrows(IllegalStateException.class, () -> {
            registry.register(sessionId, conn2);
        });
    }
    
    @Test
    void testGetGuildMembers() {
        LocalConnectionRegistry registry = new LocalConnectionRegistry();
        GuildId guild = new GuildId("guild_001");
        
        SessionId session1 = SessionId.generate();
        SessionId session2 = SessionId.generate();
        
        GatewayConnection conn1 = new GatewayConnection(session1, null, null);
        GatewayConnection conn2 = new GatewayConnection(session2, null, null);
        
        conn1.subscribeToGuild(guild);
        
        registry.register(session1, conn1);
        registry.register(session2, conn2);
        
        var members = registry.getGuildMembers(guild);
        assertEquals(1, members.size());
        assertTrue(members.stream().anyMatch(c -> c.sessionId().equals(session1)));
    }
}
