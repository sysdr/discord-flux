package com.flux.gateway.protocol;

import java.nio.ByteBuffer;

/**
 * Opcode 1: Heartbeat packet (client → server).
 * Payload: 8-byte sequence number.
 * Zero heap allocation on decode.
 */
public record Heartbeat(byte opcode, long sequence) implements GatewayPacket {
    
    public static final byte OPCODE = 1;
    
    public Heartbeat(long sequence) {
        this(OPCODE, sequence);
    }
    
    public static Heartbeat decode(ByteBuffer buffer) {
        if (buffer.remaining() < 9) { // 1 byte opcode + 8 bytes long
            throw new ProtocolException("Heartbeat buffer too small");
        }
        long seq = buffer.getLong(1); // Read from position 1 (after opcode)
        return new Heartbeat(seq);
    }
    
    @Override
    public ByteBuffer encode() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(9);
        buffer.put(OPCODE);
        buffer.putLong(sequence);
        buffer.flip();
        return buffer;
    }
}
