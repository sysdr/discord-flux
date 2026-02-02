package com.flux.gateway.protocol;

/**
 * Guild member representation.
 * Minimal fields for demonstration (production includes roles, joined_at, etc.)
 */
public record Member(
    String userId,
    String username,
    String discriminator,
    String avatar
) {
    public String toJson() {
        return String.format(
            "{\"user\":{\"id\":\"%s\",\"username\":\"%s\",\"discriminator\":\"%s\",\"avatar\":\"%s\"}}",
            userId, username, discriminator, avatar
        );
    }
    
    public static Member fromRedis(String redisJson) {
        // Parse Redis stored format: "userId:username:discriminator:avatar"
        String[] parts = redisJson.split(":");
        return new Member(
            parts[0],
            parts.length > 1 ? parts[1] : "Unknown",
            parts.length > 2 ? parts[2] : "0000",
            parts.length > 3 ? parts[3] : "default"
        );
    }
}
