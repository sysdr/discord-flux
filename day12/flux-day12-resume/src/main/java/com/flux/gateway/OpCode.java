package com.flux.gateway;

public enum OpCode {
    DISPATCH(0),        // Server -> Client: Event dispatch
    HEARTBEAT(1),       // Client -> Server: Heartbeat
    IDENTIFY(2),        // Client -> Server: Initial handshake
    STATUS_UPDATE(3),   // Client -> Server: Status update
    HELLO(10),          // Server -> Client: Initial handshake
    HEARTBEAT_ACK(11),  // Server -> Client: Heartbeat acknowledgement
    RESUME(6),          // Client -> Server: Resume connection
    RESUMED(7);         // Server -> Client: Connection resumed
    
    private final int code;
    
    OpCode(int code) {
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }
    
    public static OpCode fromCode(int code) {
        return switch(code) {
            case 0 -> DISPATCH;
            case 1 -> HEARTBEAT;
            case 2 -> IDENTIFY;
            case 3 -> STATUS_UPDATE;
            case 6 -> RESUME;
            case 7 -> RESUMED;
            case 10 -> HELLO;
            case 11 -> HEARTBEAT_ACK;
            default -> throw new IllegalArgumentException("Unknown opcode: " + code);
        };
    }
}
