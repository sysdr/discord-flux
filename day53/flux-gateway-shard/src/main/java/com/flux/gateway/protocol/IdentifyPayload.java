package com.flux.gateway.protocol;

import com.flux.gateway.shard.ShardIdentity;

/**
 * Parsed representation of the Opcode 2 IDENTIFY payload data field.
 *
 * Wire format (JSON):
 * {
 *   "op": 2,
 *   "d": {
 *     "token": "Bot MTk4NjIyNDgzNDcxOTI1MjQ4.Cl2FDQ...",
 *     "intents": 513,
 *     "shard": [shard_id, num_shards],
 *     "properties": { "os": "linux", "browser": "flux", "device": "flux" }
 *   }
 * }
 */
public record IdentifyPayload(
        String        token,
        int           intents,
        ShardIdentity shardIdentity
) {
    public IdentifyPayload {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("IDENTIFY token must not be blank");
        }
        if (shardIdentity == null) {
            throw new IllegalArgumentException("IDENTIFY must include shard array");
        }
    }
}
