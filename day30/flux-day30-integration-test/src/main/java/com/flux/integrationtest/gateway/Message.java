package com.flux.integrationtest.gateway;

/**
 * Immutable message with embedded timestamp for latency measurement.
 * Uses record for zero-overhead serialization.
 */
public record Message(
    long senderId,
    long timestamp,
    String content,
    MessageType type
) {
    public enum MessageType {
        CHAT,
        HEARTBEAT,
        SYSTEM
    }
    
    public static Message chat(long senderId, String content) {
        return new Message(senderId, System.nanoTime(), content, MessageType.CHAT);
    }
    
    public static Message heartbeat(long senderId) {
        return new Message(senderId, System.nanoTime(), "ping", MessageType.HEARTBEAT);
    }
}
