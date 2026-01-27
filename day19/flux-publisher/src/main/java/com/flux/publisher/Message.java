package com.flux.publisher;

import java.util.Map;

/**
 * Immutable message record optimized for Redis Streams.
 * Uses record pattern to eliminate boilerplate and ensure immutability.
 */
public record Message(
    String guildId,
    String channelId,
    String userId,
    String content,
    long timestamp
) {
    public Message {
        // Compact constructor for validation
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guildId cannot be null or blank");
        }
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId cannot be null or blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content cannot be null or blank");
        }
    }

    /**
     * Convert to Redis Stream fields.
     * Avoids JSON serialization - stores directly as Redis hash map.
     */
    public Map<String, String> toRedisFields() {
        return Map.of(
            "channel_id", channelId,
            "user_id", userId,
            "content", content,
            "timestamp", String.valueOf(timestamp)
        );
    }

    /**
     * Generate Redis Stream key using guild-centric routing.
     * Pattern: guild:{guild_id}:messages
     */
    public String getStreamKey() {
        return "guild:" + guildId + ":messages";
    }
}
