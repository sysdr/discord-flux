package com.flux.persistence;

public record Message(
    long id,
    String channelId,
    String userId,
    String content,
    long timestamp
) {
    public static Message create(String channelId, String userId, String content, SnowflakeGenerator idGen) {
        long id = idGen.nextId();
        return new Message(id, channelId, userId, content, System.currentTimeMillis());
    }
}
