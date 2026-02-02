package com.flux.gateway.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.UUID;

/**
 * Opcode 8: Request Guild Members
 * Client requests a chunk of guild members (lazy loading).
 */
public record ChunkRequest(
    String connectionId,
    String guildId,
    String query,      // Optional: filter members by username prefix
    int limit,         // Chunk size (typically 100-1000)
    String nonce       // Client-provided ID to match response
) {
    public ChunkRequest {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guildId cannot be null or empty");
        }
        if (limit <= 0 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        if (nonce == null) {
            nonce = UUID.randomUUID().toString();
        }
    }
    
    public static ChunkRequest fromJson(String connectionId, String json) {
        if (json == null || (json = json.trim()).isEmpty()) {
            throw new IllegalArgumentException("Empty message");
        }
        var root = JsonParser.parseString(json).getAsJsonObject();
        var d = root.has("d") && root.get("d").isJsonObject() ? root.getAsJsonObject("d") : new JsonObject();
        String guildId = d.has("guild_id") ? d.get("guild_id").getAsString() : null;
        String query = d.has("query") ? d.get("query").getAsString() : "";
        int limit = d.has("limit") ? d.get("limit").getAsInt() : 100;
        String nonce = d.has("nonce") ? d.get("nonce").getAsString() : UUID.randomUUID().toString();
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guild_id is required");
        }
        return new ChunkRequest(connectionId, guildId, query, limit, nonce);
    }
}
