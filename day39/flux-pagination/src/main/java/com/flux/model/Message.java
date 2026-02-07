package com.flux.model;

import java.time.Instant;

public record Message(
    long messageId,
    long channelId,
    long authorId,
    String content,
    Instant createdAt
) {
    public String toJson() {
        return String.format(
            "{\"messageId\":%d,\"channelId\":%d,\"authorId\":%d,\"content\":\"%s\",\"createdAt\":\"%s\"}",
            messageId, channelId, authorId, 
            content.replace("\"", "\\\""), 
            createdAt.toString()
        );
    }
}
