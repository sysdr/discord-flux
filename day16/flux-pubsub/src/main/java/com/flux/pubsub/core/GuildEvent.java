package com.flux.pubsub.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record GuildEvent(
    long guildId,
    EventType type,
    long timestamp,
    String payload
) {
    // Serialize to ByteBuffer (compact binary format)
    public ByteBuffer serialize() {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + 1 + 8 + 4 + payloadBytes.length);
        buffer.putLong(guildId);
        buffer.put(type.code());
        buffer.putLong(timestamp);
        buffer.putInt(payloadBytes.length);
        buffer.put(payloadBytes);
        buffer.flip();
        return buffer;
    }

    // Deserialize from ByteBuffer (zero-copy where possible)
    public static GuildEvent deserialize(ByteBuffer buffer) {
        long guildId = buffer.getLong();
        byte typeCode = buffer.get();
        EventType type = EventType.fromCode(typeCode);
        long timestamp = buffer.getLong();
        int payloadLen = buffer.getInt();
        byte[] payloadBytes = new byte[payloadLen];
        buffer.get(payloadBytes);
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        return new GuildEvent(guildId, type, timestamp, payload);
    }

    // Create from map (for Redis Stream entries)
    public static GuildEvent fromMap(java.util.Map<String, String> map) {
        return new GuildEvent(
            Long.parseLong(map.get("guildId")),
            EventType.valueOf(map.get("type")),
            Long.parseLong(map.get("timestamp")),
            map.get("payload")
        );
    }

    // Convert to map for Redis Stream storage
    public java.util.Map<String, String> toMap() {
        return java.util.Map.of(
            "guildId", String.valueOf(guildId),
            "type", type.name(),
            "timestamp", String.valueOf(timestamp),
            "payload", payload
        );
    }
}
