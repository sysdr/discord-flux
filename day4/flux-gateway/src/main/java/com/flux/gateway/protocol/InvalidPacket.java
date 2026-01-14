package com.flux.gateway.protocol;

import java.nio.ByteBuffer;

/**
 * Represents a malformed or unknown packet.
 */
public record InvalidPacket(byte opcode, String reason) implements GatewayPacket {
    
    @Override
    public ByteBuffer encode() {
        throw new UnsupportedOperationException("Cannot encode invalid packet");
    }
}
