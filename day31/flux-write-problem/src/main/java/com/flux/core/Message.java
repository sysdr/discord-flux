package com.flux.core;

import java.time.Instant;

/**
 * Immutable message record.
 */
public record Message(
    long id,
    String channelId,
    String content,
    Instant createdAt
) {
    public Message(long id, String channelId, String content) {
        this(id, channelId, content, Instant.now());
    }
}
