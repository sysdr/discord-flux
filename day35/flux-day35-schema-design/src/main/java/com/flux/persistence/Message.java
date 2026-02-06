package com.flux.persistence;

import java.util.UUID;

public record Message(
    long channelId,
    UUID messageId,
    long userId,
    String content,
    long timestamp
) {
    public Message {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }
        if (timestamp <= 0) {
            throw new IllegalArgumentException("Invalid timestamp");
        }
    }
}
