package com.flux.subscriber;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record Message(
    String guildId,
    String userId,
    String content,
    long timestamp
) {
    public ByteBuffer toByteBuffer() {
        // Simple frame format: guildId|userId|content|timestamp\n
        String frame = String.format("%s|%s|%s|%d\n", 
            guildId, userId, content, timestamp);
        return ByteBuffer.wrap(frame.getBytes(StandardCharsets.UTF_8));
    }

    public static Message fromRedisMap(java.util.Map<String, String> map) {
        return new Message(
            map.get("guild_id"),
            map.get("user_id"),
            map.get("content"),
            Long.parseLong(map.getOrDefault("timestamp", "0"))
        );
    }
}
