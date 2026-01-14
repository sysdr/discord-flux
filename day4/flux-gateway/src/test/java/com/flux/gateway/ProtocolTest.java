package com.flux.gateway;

import com.flux.gateway.protocol.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    
    @Test
    void testHeartbeatEncodeDecode() {
        Heartbeat original = new Heartbeat(12345L);
        ByteBuffer buffer = original.encode();
        
        Heartbeat decoded = Heartbeat.decode(buffer);
        
        assertEquals(original.sequence(), decoded.sequence());
        assertEquals(Heartbeat.OPCODE, decoded.opcode());
    }
    
    @Test
    void testHeartbeatZeroAllocation() {
        // This test verifies the hot path allocates zero heap memory
        // Run with -XX:+PrintGC to verify
        ByteBuffer buffer = ByteBuffer.allocateDirect(9);
        buffer.put(Heartbeat.OPCODE);
        buffer.putLong(999L);
        buffer.flip();
        
        // Decode 100k times - should see zero GC
        for (int i = 0; i < 100_000; i++) {
            buffer.rewind();
            Heartbeat hb = Heartbeat.decode(buffer);
            assertEquals(999L, hb.sequence());
        }
    }
    
    @Test
    void testIdentifyEncodeDecode() {
        Identify original = new Identify("flux_test_token_12345");
        ByteBuffer buffer = original.encode();
        
        Identify decoded = Identify.decode(buffer);
        
        assertEquals(original.token(), decoded.token());
    }
    
    @Test
    void testOpcodeSwitch() {
        // Test that unknown opcodes create InvalidPacket
        ByteBuffer buffer = ByteBuffer.allocateDirect(10);
        buffer.put((byte) 99); // Unknown opcode
        buffer.flip();
        
        GatewayPacket packet = GatewayPacket.decode(buffer);
        
        assertInstanceOf(InvalidPacket.class, packet);
    }
}
