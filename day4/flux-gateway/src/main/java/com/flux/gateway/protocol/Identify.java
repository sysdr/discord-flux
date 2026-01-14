package com.flux.gateway.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Opcode 2: Identify (client → server).
 * Client authentication packet. Contains token (string).
 */
public record Identify(byte opcode, String token) implements GatewayPacket {
    
    public static final byte OPCODE = 2;
    
    public Identify(String token) {
        this(OPCODE, token);
    }
    
    public static Identify decode(ByteBuffer buffer) {
        if (buffer.remaining() < 3) {
            throw new ProtocolException("Identify buffer too small");
        }
        
        // Format: [opcode:1][tokenLen:2][token:N]
        short tokenLen = buffer.getShort(1);
        if (buffer.remaining() < 3 + tokenLen) {
            throw new ProtocolException("Identify token truncated");
        }
        
        byte[] tokenBytes = new byte[tokenLen];
        buffer.position(3);
        buffer.get(tokenBytes);
        
        String token = new String(tokenBytes, StandardCharsets.UTF_8);
        return new Identify(token);
    }
    
    @Override
    public ByteBuffer encode() {
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocateDirect(3 + tokenBytes.length);
        buffer.put(OPCODE);
        buffer.putShort((short) tokenBytes.length);
        buffer.put(tokenBytes);
        buffer.flip();
        return buffer;
    }
}
