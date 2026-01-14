package com.flux.gateway.protocol;

import java.nio.ByteBuffer;

/**
 * Sealed interface representing all possible Gateway protocol packets.
 * Opcodes are defined as constants for fast switching.
 */
public sealed interface GatewayPacket permits Hello, Identify, Heartbeat, HeartbeatAck, Dispatch, InvalidPacket {
    
    byte opcode();
    
    /**
     * Decode packet from ByteBuffer without String allocation.
     * Reads opcode from position 0.
     */
    static GatewayPacket decode(ByteBuffer buffer) {
        if (buffer.remaining() < 1) {
            return new InvalidPacket((byte) -1, "Empty buffer");
        }
        
        byte op = buffer.get(0);
        
        return switch (op) {
            case 0 -> Dispatch.decode(buffer);
            case 1 -> Heartbeat.decode(buffer);
            case 2 -> Identify.decode(buffer);
            case 10 -> Hello.decode(buffer);
            case 11 -> HeartbeatAck.decode(buffer);
            default -> new InvalidPacket(op, "Unknown opcode: " + op);
        };
    }
    
    /**
     * Encode packet to ByteBuffer for transmission.
     */
    ByteBuffer encode();
}
