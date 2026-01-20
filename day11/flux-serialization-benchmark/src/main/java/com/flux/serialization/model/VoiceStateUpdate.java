package com.flux.serialization.model;

public record VoiceStateUpdate(
    long userId,
    long guildId,
    long channelId,
    boolean muted,
    boolean deafened
) {
    public static VoiceStateUpdate random() {
        return new VoiceStateUpdate(
            (long)(Math.random() * 1_000_000_000),
            (long)(Math.random() * 1_000_000),
            (long)(Math.random() * 10_000),
            Math.random() > 0.5,
            Math.random() > 0.5
        );
    }
}
