package com.flux.gateway.model;

import com.flux.gateway.intent.GatewayIntent;

public record GatewayEvent(
    String type,
    long requiredIntent,
    Object data,
    int estimatedSize,
    long timestamp
) {
    public GatewayEvent(String type, long requiredIntent, Object data, int estimatedSize) {
        this(type, requiredIntent, data, estimatedSize, System.currentTimeMillis());
    }

    // Factory methods for different event types
    public static GatewayEvent messageCreate(String guildId, String content) {
        return new GatewayEvent(
            "MESSAGE_CREATE",
            GatewayIntent.GUILD_MESSAGES.mask,
            new Message(guildId, content),
            450
        );
    }

    public static GatewayEvent presenceUpdate(String userId, String status) {
        return new GatewayEvent(
            "PRESENCE_UPDATE",
            GatewayIntent.GUILD_PRESENCES.mask,
            new Presence(userId, status),
            280
        );
    }

    public static GatewayEvent typingStart(String guildId, String userId) {
        return new GatewayEvent(
            "TYPING_START",
            GatewayIntent.GUILD_MESSAGE_TYPING.mask,
            new Typing(guildId, userId),
            150
        );
    }

    public static GatewayEvent voiceStateUpdate(String guildId, String channelId) {
        return new GatewayEvent(
            "VOICE_STATE_UPDATE",
            GatewayIntent.GUILD_VOICE_STATES.mask,
            new VoiceState(guildId, channelId),
            320
        );
    }

    public static GatewayEvent guildMemberAdd(String guildId, String userId) {
        return new GatewayEvent(
            "GUILD_MEMBER_ADD",
            GatewayIntent.GUILD_MEMBERS.mask,
            new Member(guildId, userId),
            400
        );
    }

    public static GatewayEvent reactionAdd(String messageId, String emoji) {
        return new GatewayEvent(
            "MESSAGE_REACTION_ADD",
            GatewayIntent.GUILD_MESSAGE_REACTIONS.mask,
            new Reaction(messageId, emoji),
            180
        );
    }

    // Nested data classes
    public record Message(String guildId, String content) {}
    public record Presence(String userId, String status) {}
    public record Typing(String guildId, String userId) {}
    public record VoiceState(String guildId, String channelId) {}
    public record Member(String guildId, String userId) {}
    public record Reaction(String messageId, String emoji) {}
}
