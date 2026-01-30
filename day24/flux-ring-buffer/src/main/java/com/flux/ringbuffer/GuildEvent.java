package com.flux.ringbuffer;

/**
 * Represents a guild event (message, presence update, etc).
 */
public record GuildEvent(
    long eventId,
    String guildId,
    String channelId,
    String payload,
    long timestamp
) {
    public static GuildEvent create(long id, String guildId, String payload) {
        return new GuildEvent(id, guildId, "channel-1", payload, System.currentTimeMillis());
    }
}
