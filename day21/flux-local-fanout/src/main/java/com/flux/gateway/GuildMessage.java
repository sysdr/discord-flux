package com.flux.gateway;

public record GuildMessage(
    String guildId,
    String userId,
    String content,
    long timestamp
) {
    public GuildMessage(String guildId, String userId, String content) {
        this(guildId, userId, content, System.currentTimeMillis());
    }
}
