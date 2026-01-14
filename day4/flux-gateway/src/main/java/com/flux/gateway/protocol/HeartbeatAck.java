package com.flux.gateway.protocol;

import java.nio.ByteBuffer;

/**
 * Opcode 11: HeartbeatAck (server → client).
 * Server responds with this to acknowledge client heartbeat.
 */
public record HeartbeatAck(byte opcode) implements GatewayPacket {
    
    public static final byte OPCODE = 11;
    
    public HeartbeatAck() {
        this(OPCODE);
    }
    
    public static HeartbeatAck decode(ByteBuffer buffer) {
        return new HeartbeatAck();
    }
    
    @Override
    public ByteBuffer encode() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1);
        buffer.put(OPCODE);
        buffer.flip();
        return buffer;
    }
}
