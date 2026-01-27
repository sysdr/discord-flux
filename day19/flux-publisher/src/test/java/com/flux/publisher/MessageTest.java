package com.flux.publisher;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {
    
    @Test
    void testMessageCreation() {
        Message msg = new Message(
            "guild-123",
            "channel-456",
            "user-789",
            "Hello, world!",
            System.currentTimeMillis()
        );
        
        assertEquals("guild-123", msg.guildId());
        assertEquals("channel-456", msg.channelId());
        assertEquals("user-789", msg.userId());
        assertEquals("Hello, world!", msg.content());
    }
    
    @Test
    void testToRedisFields() {
        Message msg = new Message(
            "guild-123",
            "channel-456",
            "user-789",
            "Test content",
            1234567890L
        );
        
        var fields = msg.toRedisFields();
        assertEquals("channel-456", fields.get("channel_id"));
        assertEquals("user-789", fields.get("user_id"));
        assertEquals("Test content", fields.get("content"));
        assertEquals("1234567890", fields.get("timestamp"));
    }
    
    @Test
    void testGetStreamKey() {
        Message msg = new Message(
            "guild-123",
            "channel-456",
            "user-789",
            "Test",
            System.currentTimeMillis()
        );
        
        assertEquals("guild:guild-123:messages", msg.getStreamKey());
    }
    
    @Test
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Message(null, "ch", "u", "content", 123L)
        );
        assertThrows(IllegalArgumentException.class, () -> 
            new Message("", "ch", "u", "content", 123L)
        );
        assertThrows(IllegalArgumentException.class, () -> 
            new Message("g", null, "u", "content", 123L)
        );
    }
}
