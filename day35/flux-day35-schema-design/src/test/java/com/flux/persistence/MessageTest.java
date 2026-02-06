package com.flux.persistence;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {
    @Test void testMessageCreation() {
        var msg = new Message(12345L, UUID.randomUUID(), 67890L, "Test message", System.currentTimeMillis());
        assertNotNull(msg);
        assertEquals("Test message", msg.content());
    }
    @Test void testEmptyContentValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Message(1L, UUID.randomUUID(), 2L, "", System.currentTimeMillis()));
    }
    @Test void testInvalidTimestampValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Message(1L, UUID.randomUUID(), 2L, "content", -1));
    }
}
