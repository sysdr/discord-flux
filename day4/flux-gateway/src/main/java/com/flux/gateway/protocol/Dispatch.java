package com.flux.gateway.protocol;

import java.nio.ByteBuffer;

/**
 * Opcode 0: Dispatch (server → client).
 * Server-sent events (messages, presence updates, etc.).
 * We don't parse payload yet - just forward raw bytes.
 */
public record Dispatch(byte opcode, ByteBuffer payload) implements GatewayPacket {
    
    public static final byte OPCODE = 0;
    
    public Dispatch(ByteBuffer payload) {
        this(OPCODE, payload);
    }
    
    public static Dispatch decode(ByteBuffer buffer) {
        // Create slice of remaining bytes (after opcode)
        ByteBuffer payload = buffer.slice(1, buffer.remaining() - 1);
        return new Dispatch(payload);
    }
    
    @Override
    public ByteBuffer encode() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1 + payload.remaining());
        buffer.put(OPCODE);
        buffer.put(payload.duplicate());
        buffer.flip();
        return buffer;
    }
}
