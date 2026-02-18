package com.flux.shard.router;

/**
 * Discord-style shard routing using Snowflake ID bitwise operations.
 * Zero-allocation, pure function design for maximum throughput.
 */
public final class ShardRouter {
    
    /**
     * Discord Snowflake IDs have timestamp in upper bits.
     * Shifting right by 22 isolates timestamp portion for temporal locality.
     */
    private static final int SNOWFLAKE_SHIFT = 22;
    
    private ShardRouter() {
        throw new AssertionError("Utility class - do not instantiate");
    }
    
    /**
     * Calculate shard ID for a given guild using Discord's algorithm.
     * 
     * @param guildId Discord Snowflake ID (64-bit)
     * @param totalShards Total number of shards in the cluster
     * @return Shard ID in range [0, totalShards)
     */
    public static int calculateShard(long guildId, int totalShards) {
        if (totalShards <= 0) {
            throw new IllegalArgumentException("Total shards must be positive");
        }
        // Extract timestamp bits and modulo into shard range
        return (int) ((guildId >> SNOWFLAKE_SHIFT) % totalShards);
    }
    
    /**
     * Batch calculate shards for multiple guilds.
     * Useful for prefetching routing decisions.
     */
    public static int[] calculateShards(long[] guildIds, int totalShards) {
        int[] shards = new int[guildIds.length];
        for (int i = 0; i < guildIds.length; i++) {
            shards[i] = calculateShard(guildIds[i], totalShards);
        }
        return shards;
    }
    
    /**
     * Validate if a guild should route to this gateway instance.
     * 
     * @param guildId Guild Snowflake ID
     * @param totalShards Total shards across cluster
     * @param shardStart Inclusive start of this instance's shard range
     * @param shardEnd Exclusive end of this instance's shard range
     * @return true if guild belongs to this instance
     */
    public static boolean belongsToInstance(long guildId, int totalShards, 
                                           int shardStart, int shardEnd) {
        int shard = calculateShard(guildId, totalShards);
        return shard >= shardStart && shard < shardEnd;
    }
}
