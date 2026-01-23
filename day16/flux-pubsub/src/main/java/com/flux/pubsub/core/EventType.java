package com.flux.pubsub.core;

public enum EventType {
    MESSAGE_CREATE(1),
    MESSAGE_UPDATE(2),
    MESSAGE_DELETE(3),
    MEMBER_JOIN(4),
    MEMBER_LEAVE(5),
    TYPING_START(6),
    PRESENCE_UPDATE(7);

    private final byte code;

    EventType(int code) {
        this.code = (byte) code;
    }

    public byte code() {
        return code;
    }

    public static EventType fromCode(byte code) {
        return switch (code) {
            case 1 -> MESSAGE_CREATE;
            case 2 -> MESSAGE_UPDATE;
            case 3 -> MESSAGE_DELETE;
            case 4 -> MEMBER_JOIN;
            case 5 -> MEMBER_LEAVE;
            case 6 -> TYPING_START;
            case 7 -> PRESENCE_UPDATE;
            default -> throw new IllegalArgumentException("Unknown event code: " + code);
        };
    }
}
