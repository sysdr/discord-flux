package com.flux;

import com.flux.model.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {
    @Test
    void createMessage() {
        Message msg = Message.create(1L, 2L, "Hello");
        assertEquals(1L, msg.channelId());
        assertEquals(2L, msg.userId());
        assertEquals("Hello", msg.content());
        assertNotNull(msg.messageId());
        assertNotNull(msg.createdAt());
    }

    @Test
    void contentValidation() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Message(1L, java.util.UUID.randomUUID(), 1L, null, java.time.Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> 
            new Message(1L, java.util.UUID.randomUUID(), 1L, "x".repeat(2001), java.time.Instant.now()));
    }
}
