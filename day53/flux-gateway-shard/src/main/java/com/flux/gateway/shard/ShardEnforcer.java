package com.flux.gateway.shard;

import com.flux.gateway.protocol.IdentifyPayload;

import java.util.regex.Pattern;

/**
 * Validates and enforces constraints on IDENTIFY payloads before
 * the session is submitted to ShardRegistry.
 *
 * Responsibilities:
 *  1. Token format validation (prefix check, not cryptographic auth)
 *  2. Shard bounds enforcement
 *  3. Intents validation
 *  4. Rate limiting hook (stubbed — see Day 54)
 *
 * This class is stateless. All mutable state lives in ShardRegistry.
 */
public final class ShardEnforcer {

    // Discord bot tokens start with "Bot " followed by base64 segments
    private static final Pattern BOT_TOKEN_PATTERN =
        Pattern.compile("^Bot [A-Za-z0-9._-]{20,}$");

    private static final int MAX_SUPPORTED_SHARDS = 4096;
    private static final int MIN_INTENTS          = 0;

    private ShardEnforcer() {}

    public sealed interface ValidationResult {
        record Valid()                                       implements ValidationResult {}
        record Invalid(String reason, boolean resumable)    implements ValidationResult {}
    }

    /**
     * Validates an IDENTIFY payload. Returns Valid or Invalid with reason.
     *
     * Note: We do NOT validate the token cryptographically here.
     * That belongs to an AuthService call (Day 58). We only check structure.
     */
    public static ValidationResult validate(IdentifyPayload payload) {
        // Token structure check
        if (!BOT_TOKEN_PATTERN.matcher(payload.token()).matches()) {
            return new ValidationResult.Invalid(
                "Token format invalid — expected 'Bot <base64>'", false);
        }

        // Shard bounds (ShardIdentity constructor already validates, but we
        // catch it here to return a typed result instead of propagating exceptions)
        var identity = payload.shardIdentity();
        if (identity.numShards() > MAX_SUPPORTED_SHARDS) {
            return new ValidationResult.Invalid(
                "numShards %d exceeds gateway maximum of %d"
                    .formatted(identity.numShards(), MAX_SUPPORTED_SHARDS), false);
        }

        // Intents range
        if (payload.intents() < MIN_INTENTS) {
            return new ValidationResult.Invalid(
                "Intents value %d is invalid".formatted(payload.intents()), false);
        }

        return new ValidationResult.Valid();
    }
}
