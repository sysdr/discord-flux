package com.flux.gateway.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Opcode 10: Hello (server → client).
 * Sent immediately after connection. Contains heartbeat_interval.
 */
public record Hello(byte opcode, int heartbeatInterval) implements GatewayPacket {
    
    public static final byte OPCODE = 10;
    
    public Hello(int heartbeatInterval) {
        this(OPCODE, heartbeatInterval);
    }
    
    public static Hello decode(ByteBuffer buffer) {
        if (buffer.remaining() < 5) {
            throw new ProtocolException("Hello buffer too small");
        }
        int interval = buffer.getInt(1);
        return new Hello(interval);
    }
    
    @Override
    public ByteBuffer encode() {
        // Format: [opcode:1][interval:4]
        ByteBuffer buffer = ByteBuffer.allocateDirect(5);
        buffer.put(OPCODE);
        buffer.putInt(heartbeatInterval);
        buffer.flip();
        return buffer;
    }
}
