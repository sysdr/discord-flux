package com.flux.gateway;

public enum Opcode {
    DISPATCH(0),
    HEARTBEAT(1),
    IDENTIFY(2),
    HEARTBEAT_ACK(11);
    
    private final int value;
    
    Opcode(int value) {
        this.value = value;
    }
    
    public int value() {
        return value;
    }
    
    public static Opcode fromValue(int value) {
        return switch (value) {
            case 0 -> DISPATCH;
            case 1 -> HEARTBEAT;
            case 2 -> IDENTIFY;
            case 11 -> HEARTBEAT_ACK;
            default -> throw new IllegalArgumentException("Unknown opcode: " + value);
        };
    }
}
