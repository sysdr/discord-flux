package com.flux.model;

import java.nio.charset.StandardCharsets;

public record Message(
    SnowflakeId id,
    long channelId,
    long authorId,
    String content,
    long createdAt
) {
    public int estimatedSizeBytes() {
        return 8 + 8 + 8 + content.getBytes(StandardCharsets.UTF_8).length + 8;
    }
    
    public static Message create(long channelId, long authorId, String content) {
        SnowflakeId id = SnowflakeId.generate();
        return new Message(id, channelId, authorId, content, System.currentTimeMillis());
    }
}
