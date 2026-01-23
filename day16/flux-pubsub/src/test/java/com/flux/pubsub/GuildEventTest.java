package com.flux.pubsub;

import com.flux.pubsub.core.EventType;
import com.flux.pubsub.core.GuildEvent;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class GuildEventTest {

    @Test
    void testSerialization() {
        GuildEvent original = new GuildEvent(
            1001L,
            EventType.MESSAGE_CREATE,
            System.currentTimeMillis(),
            "Hello, World!"
        );

        ByteBuffer buffer = original.serialize();
        GuildEvent deserialized = GuildEvent.deserialize(buffer);

        assertEquals(original.guildId(), deserialized.guildId());
        assertEquals(original.type(), deserialized.type());
        assertEquals(original.timestamp(), deserialized.timestamp());
        assertEquals(original.payload(), deserialized.payload());
    }

    @Test
    void testMapConversion() {
        GuildEvent event = new GuildEvent(
            2002L,
            EventType.MEMBER_JOIN,
            12345L,
            "User joined"
        );

        var map = event.toMap();
        GuildEvent restored = GuildEvent.fromMap(map);

        assertEquals(event.guildId(), restored.guildId());
        assertEquals(event.type(), restored.type());
        assertEquals(event.payload(), restored.payload());
    }
}
