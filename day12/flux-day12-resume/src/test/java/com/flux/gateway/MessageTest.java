package com.flux.gateway;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;

class MessageTest {
    @Test
    void testSerializationRoundTrip() {
        Message original = new Message(OpCode.DISPATCH, 42, "{\"user\":\"test\"}");
        ByteBuffer serialized = original.serialize();
        Message deserialized = Message.deserialize(serialized);
        
        assertEquals(original.op(), deserialized.op());
        assertEquals(original.seq(), deserialized.seq());
        assertEquals(original.data(), deserialized.data());
    }
    
    @Test
    void testJsonConversion() {
        Message msg = new Message(OpCode.RESUME, 100, "{\"session_id\":\"abc\"}");
        String json = msg.toJson();
        
        assertTrue(json.contains("\"op\":6"));
        assertTrue(json.contains("\"s\":100"));
        assertTrue(json.contains("\"session_id\":\"abc\""));
    }
    
    @Test
    void testLargeData() {
        String largeData = "x".repeat(500);
        Message msg = new Message(OpCode.DISPATCH, 1, largeData);
        
        ByteBuffer serialized = msg.serialize();
        Message deserialized = Message.deserialize(serialized);
        
        assertEquals(largeData, deserialized.data());
    }
}
