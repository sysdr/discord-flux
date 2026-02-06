package com.flux.model;

import java.util.UUID;

/**
 * Immutable message record representing a chat message.
 * In production, this would map to a Cassandra row.
 */
public record Message(
    UUID userId,
    int bucketId,
    long messageId,      // Would be timeuuid in Cassandra
    long timestamp,
    String content
) {
    public Message {
        if (userId == null) throw new IllegalArgumentException("userId cannot be null");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content cannot be empty");
        }
    }
    
    /**
     * Calculate partition key as it would appear in Cassandra.
     * Format: "userId:bucketId"
     */
    public String partitionKey() {
        return userId.toString() + ":" + bucketId;
    }
}
