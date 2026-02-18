package com.flux.gateway.protocol;

/**
 * Discord Gateway Opcodes (subset relevant to IDENTIFY handshake).
 * Reference: https://discord.com/developers/docs/topics/opcodes-and-status-codes
 */
public enum GatewayOpcode {
    DISPATCH         (0),
    HEARTBEAT        (1),
    IDENTIFY         (2),
    PRESENCE_UPDATE  (3),
    VOICE_STATE      (4),
    RESUME           (6),
    RECONNECT        (7),
    INVALID_SESSION  (9),
    HELLO            (10),
    HEARTBEAT_ACK    (11);

    public final int code;

    GatewayOpcode(int code) {
        this.code = code;
    }

    public static GatewayOpcode fromCode(int code) {
        return switch (code) {
            case 0  -> DISPATCH;
            case 1  -> HEARTBEAT;
            case 2  -> IDENTIFY;
            case 3  -> PRESENCE_UPDATE;
            case 4  -> VOICE_STATE;
            case 6  -> RESUME;
            case 7  -> RECONNECT;
            case 9  -> INVALID_SESSION;
            case 10 -> HELLO;
            case 11 -> HEARTBEAT_ACK;
            default -> throw new IllegalArgumentException("Unknown opcode: " + code);
        };
    }
}
