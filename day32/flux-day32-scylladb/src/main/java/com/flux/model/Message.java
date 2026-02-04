package com.flux.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable message record representing a chat message.
 * Uses Java 21 record for concise, type-safe data modeling.
 */
public record Message(
    long channelId,
    UUID messageId,
    long userId,
    String content,
    Instant createdAt
) {
    public Message {
        if (content == null || content.length() > 2000) {
            throw new IllegalArgumentException("Content must be 1-2000 characters");
        }
    }

    public static Message create(long channelId, long userId, String content) {
        return new Message(
            channelId,
            UUID.randomUUID(), // Will be replaced with TIMEUUID by Cassandra
            userId,
            content,
            Instant.now()
        );
    }
}
