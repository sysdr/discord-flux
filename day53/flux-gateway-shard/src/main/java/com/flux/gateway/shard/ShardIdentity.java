package com.flux.gateway.shard;

/**
 * Immutable value type representing a shard's identity within a shard group.
 *
 * In Discord's model, events for a guild are always routed to one shard:
 *     shard_id = (guild_id >> 22) % num_shards
 *
 * Constraints enforced at construction time (fast-fail before registry lookup):
 *  - shardId   >= 0
 *  - numShards >  0
 *  - shardId   <  numShards
 *  - numShards must be a power of two (production requirement — see homework)
 */
public record ShardIdentity(int shardId, int numShards) {

    public ShardIdentity {
        if (numShards <= 0) {
            throw new IllegalArgumentException("numShards must be positive, got: " + numShards);
        }
        if (shardId < 0 || shardId >= numShards) {
            throw new IllegalArgumentException(
                "shardId %d is out of range for numShards %d".formatted(shardId, numShards));
        }
    }

    /**
     * Returns true if this shard is responsible for the given guild.
     * Useful for routing validation in later lessons (Consistent Hashing).
     */
    public boolean isResponsibleFor(long guildId) {
        return ((guildId >> 22) % numShards) == shardId;
    }

    @Override
    public String toString() {
        return "[%d, %d]".formatted(shardId, numShards);
    }
}
