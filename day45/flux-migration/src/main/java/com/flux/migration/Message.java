package com.flux.migration;

import java.time.Instant;

/**
 * Immutable message record matching Cassandra schema
 * Using records (Java 14+) eliminates boilerplate and ensures immutability
 */
public record Message(
    long id,
    long channelId,
    String userId,
    String content,
    Instant timestamp
) {
    public Message {
        // Compact constructor for validation
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content cannot be blank");
        }
    }
}
