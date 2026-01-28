package com.flux.gateway.intent;

public enum GatewayIntent {
    GUILDS(1L << 0, "Guild create/update/delete"),
    GUILD_MEMBERS(1L << 1, "Member join/leave/update"),
    GUILD_MODERATION(1L << 2, "Ban/unban events"),
    GUILD_EMOJIS(1L << 3, "Emoji create/update/delete"),
    GUILD_INTEGRATIONS(1L << 4, "Integration updates"),
    GUILD_WEBHOOKS(1L << 5, "Webhook create/update/delete"),
    GUILD_INVITES(1L << 6, "Invite create/delete"),
    GUILD_VOICE_STATES(1L << 7, "Voice state updates"),
    GUILD_PRESENCES(1L << 8, "Presence updates (privileged)"),
    GUILD_MESSAGES(1L << 9, "Message create/update/delete"),
    GUILD_MESSAGE_REACTIONS(1L << 10, "Reaction add/remove"),
    GUILD_MESSAGE_TYPING(1L << 11, "Typing indicators"),
    DIRECT_MESSAGES(1L << 12, "DM message events"),
    DIRECT_MESSAGE_REACTIONS(1L << 13, "DM reaction events"),
    DIRECT_MESSAGE_TYPING(1L << 14, "DM typing indicators"),
    MESSAGE_CONTENT(1L << 15, "Message content (privileged)"),
    GUILD_SCHEDULED_EVENTS(1L << 16, "Scheduled event updates"),
    AUTO_MODERATION_CONFIGURATION(1L << 20, "Auto-mod config"),
    AUTO_MODERATION_EXECUTION(1L << 21, "Auto-mod execution");

    public final long mask;
    public final String description;

    GatewayIntent(long mask, String description) {
        this.mask = mask;
        this.description = description;
    }

    public static long combine(GatewayIntent... intents) {
        long combined = 0L;
        for (var intent : intents) {
            combined |= intent.mask;
        }
        return combined;
    }

    public static long getPrivilegedMask() {
        return GUILD_PRESENCES.mask | MESSAGE_CONTENT.mask;
    }

    public static boolean isPrivileged(long intentMask) {
        return (intentMask & getPrivilegedMask()) != 0;
    }

    public static String describe(long intentMask) {
        var sb = new StringBuilder("Intents[");
        boolean first = true;
        for (var intent : values()) {
            if ((intentMask & intent.mask) != 0) {
                if (!first) sb.append(", ");
                sb.append(intent.name());
                first = false;
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
