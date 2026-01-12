package com.flux.gateway;

import java.nio.ByteBuffer;

/**
 * Immutable WebSocket frame representation (zero-copy view).
 * Uses Java 21 record for compact, efficient data structure.
 */
public record WebSocketFrame(
    boolean fin,
    int opcode,
    boolean masked,
    ByteBuffer payload
) {
    // WebSocket Opcodes
    public static final int OPCODE_CONTINUATION = 0x0;
    public static final int OPCODE_TEXT = 0x1;
    public static final int OPCODE_BINARY = 0x2;
    public static final int OPCODE_CLOSE = 0x8;
    public static final int OPCODE_PING = 0x9;
    public static final int OPCODE_PONG = 0xA;

    public boolean isFinal() {
        return fin;
    }

    public boolean isControlFrame() {
        return (opcode & 0x08) != 0;
    }

    public String opcodeToString() {
        return switch (opcode) {
            case OPCODE_CONTINUATION -> "CONTINUATION";
            case OPCODE_TEXT -> "TEXT";
            case OPCODE_BINARY -> "BINARY";
            case OPCODE_CLOSE -> "CLOSE";
            case OPCODE_PING -> "PING";
            case OPCODE_PONG -> "PONG";
            default -> "UNKNOWN(0x" + Integer.toHexString(opcode) + ")";
        };
    }

    public String getPayloadAsText() {
        if (payload == null || !payload.hasRemaining()) {
            return "";
        }
        byte[] bytes = new byte[payload.remaining()];
        payload.mark();
        payload.get(bytes);
        payload.reset();
        return new String(bytes);
    }
}
