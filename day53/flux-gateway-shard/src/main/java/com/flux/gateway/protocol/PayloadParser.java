package com.flux.gateway.protocol;

import com.flux.gateway.shard.ShardIdentity;

import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Minimal, zero-dependency JSON field extractor for Gateway payloads.
 *
 * Limitations (by design — this is not a general JSON parser):
 *  - Does not handle escaped quotes inside string values.
 *  - Assumes well-formed JSON from well-behaved clients.
 *  - Malformed input results in empty Optional / exceptions handled by caller.
 *
 * Why not use a library? At this layer, adding Jackson adds 2MB to the JAR,
 * introduces reflection-based deserialization, and hides what's actually in
 * the byte stream. At 1M connections we parse millions of IDENTIFY frames;
 * understanding the exact allocation profile of this code matters.
 */
public final class PayloadParser {

    private static final Pattern OP_PATTERN     = Pattern.compile("\"op\"\\s*:\\s*(\\d+)");
    private static final Pattern TOKEN_PATTERN  = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern INTENTS_PATTERN= Pattern.compile("\"intents\"\\s*:\\s*(\\d+)");
    private static final Pattern SHARD_PATTERN  = Pattern.compile("\"shard\"\\s*:\\s*\\[(\\d+)\\s*,\\s*(\\d+)\\]");

    private PayloadParser() {}

    public static OptionalInt extractOpcode(String json) {
        var m = OP_PATTERN.matcher(json);
        return m.find() ? OptionalInt.of(Integer.parseInt(m.group(1))) : OptionalInt.empty();
    }

    /**
     * Parses a full Opcode 2 IDENTIFY payload string into an {@link IdentifyPayload}.
     *
     * @param json the full payload JSON string
     * @return parsed record
     * @throws IllegalArgumentException if required fields are missing or shard is invalid
     */
    public static IdentifyPayload parseIdentify(String json) {
        var tokenMatcher   = TOKEN_PATTERN.matcher(json);
        var intentsMatcher = INTENTS_PATTERN.matcher(json);
        var shardMatcher   = SHARD_PATTERN.matcher(json);

        if (!tokenMatcher.find()) {
            throw new IllegalArgumentException("IDENTIFY payload missing 'token' field");
        }
        if (!shardMatcher.find()) {
            throw new IllegalArgumentException("IDENTIFY payload missing 'shard' array");
        }

        var token     = tokenMatcher.group(1);
        var intents   = intentsMatcher.find() ? Integer.parseInt(intentsMatcher.group(1)) : 0;
        var shardId   = Integer.parseInt(shardMatcher.group(1));
        var numShards = Integer.parseInt(shardMatcher.group(2));

        return new IdentifyPayload(token, intents, new ShardIdentity(shardId, numShards));
    }

    // ── Outbound payload builders ──────────────────────────────────────────

    public static String buildHello(int heartbeatIntervalMs) {
        return """
               {"op":10,"d":{"heartbeat_interval":%d}}\
               """.formatted(heartbeatIntervalMs);
    }

    public static String buildHeartbeatAck() {
        return "{\"op\":11}";
    }

    public static String buildInvalidSession(boolean resumable) {
        return "{\"op\":9,\"d\":%b}".formatted(resumable);
    }

    public static String buildReady(int shardId, int numShards, String sessionId, boolean evicted) {
        return """
               {"op":0,"t":"READY","d":{"v":10,"session_id":"%s","shard":[%d,%d],"evicted":%b}}\
               """.formatted(sessionId, shardId, numShards, evicted);
    }
}
