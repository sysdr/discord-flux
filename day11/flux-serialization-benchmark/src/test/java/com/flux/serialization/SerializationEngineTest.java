package com.flux.serialization;

import com.flux.serialization.engine.*;
import com.flux.serialization.model.VoiceStateUpdate;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class SerializationEngineTest {
    
    @Test
    void testJsonEngine() {
        testEngine(new JsonEngine());
    }
    
    @Test
    void testProtobufEngine() {
        testEngine(new ProtobufEngine());
    }
    
    @Test
    void testCustomBinaryEngine() {
        testEngine(new CustomBinaryEngine());
    }
    
    private void testEngine(SerializationEngine engine) {
        VoiceStateUpdate original = new VoiceStateUpdate(
            123456789L, 987654L, 456123L, true, false
        );
        
        ByteBuffer buf = ByteBuffer.allocate(512);
        
        // Serialize
        int size = engine.serialize(original, buf);
        assertTrue(size > 0, "Serialized size should be positive");
        assertTrue(size <= engine.estimatedSize() * 2, 
                   "Serialized size should be close to estimate");
        
        // Deserialize
        buf.flip();
        VoiceStateUpdate deserialized = engine.deserialize(buf);
        
        // Verify
        assertEquals(original.userId(), deserialized.userId());
        assertEquals(original.guildId(), deserialized.guildId());
        assertEquals(original.channelId(), deserialized.channelId());
        assertEquals(original.muted(), deserialized.muted());
        assertEquals(original.deafened(), deserialized.deafened());
    }
}
