package com.flux.shard;

import com.flux.shard.generator.SnowflakeGenerator;
import com.flux.shard.router.ShardRouter;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShardRouterTest {
    
    @Test
    void testBasicShardCalculation() {
        long guildId = 123456789L << 22; // Timestamp portion
        int shard = ShardRouter.calculateShard(guildId, 16);
        
        assertTrue(shard >= 0 && shard < 16, "Shard should be in valid range");
    }
    
    @Test
    void testConsistentSharding() {
        long guildId = 987654321L << 22;
        int shard1 = ShardRouter.calculateShard(guildId, 32);
        int shard2 = ShardRouter.calculateShard(guildId, 32);
        
        assertEquals(shard1, shard2, "Same guild should always map to same shard");
    }
    
    @Test
    void testDistributionBalance() {
        int totalShards = 64;
        int numGuilds = 10000;
        Map<Integer, Integer> shardCounts = new HashMap<>();
        
        SnowflakeGenerator generator = new SnowflakeGenerator(1, 1);
        // Spread guild IDs across time (30 days) so shard distribution is meaningful
        long dayMs = 24L * 60 * 60 * 1000;
        
        for (int i = 0; i < numGuilds; i++) {
            long offset = (i * 37L % (30 * dayMs)); // spread over ~30 days
            long guildId = generator.generateWithOffset(offset);
            int shard = ShardRouter.calculateShard(guildId, totalShards);
            shardCounts.merge(shard, 1, Integer::sum);
        }
        
        // All shards should be assigned when IDs are spread over time
        assertEquals(totalShards, shardCounts.size(), 
            "All shards should receive at least one guild");
        
        // Calculate standard deviation
        double mean = numGuilds / (double) totalShards;
        double variance = shardCounts.values().stream()
            .mapToDouble(count -> Math.pow(count - mean, 2))
            .average()
            .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        
        // Standard deviation should be < 20% of mean for good distribution
        assertTrue(stdDev < mean * 0.2, 
            "Distribution should be relatively balanced (stdDev=" + stdDev + 
            ", mean=" + mean + ")");
    }
    
    @Test
    void testBelongsToInstance() {
        long guildId = 555555L << 22;
        int totalShards = 64;
        int shard = ShardRouter.calculateShard(guildId, totalShards);
        
        // Instance owns shards 0-15
        if (shard < 16) {
            assertTrue(ShardRouter.belongsToInstance(guildId, totalShards, 0, 16));
            assertFalse(ShardRouter.belongsToInstance(guildId, totalShards, 16, 32));
        } else {
            assertFalse(ShardRouter.belongsToInstance(guildId, totalShards, 0, 16));
        }
    }
    
    @Test
    void testTemporalLocality() {
        SnowflakeGenerator gen = new SnowflakeGenerator(1, 1);
        int totalShards = 32;
        
        // Generate guilds with close timestamps
        long guild1 = gen.nextId();
        long guild2 = gen.nextId();
        long guild3 = gen.nextId();
        
        int shard1 = ShardRouter.calculateShard(guild1, totalShards);
        int shard2 = ShardRouter.calculateShard(guild2, totalShards);
        int shard3 = ShardRouter.calculateShard(guild3, totalShards);
        
        // Guilds created close together should have close shard IDs
        // (not guaranteed, but highly probable)
        int maxSpread = Math.abs(Math.max(shard1, Math.max(shard2, shard3)) - 
                                Math.min(shard1, Math.min(shard2, shard3)));
        
        assertTrue(maxSpread < totalShards / 4, 
            "Temporally close guilds should cluster in shard space");
    }
    
    @Test
    void testBatchCalculation() {
        long[] guildIds = {100L << 22, 200L << 22, 300L << 22};
        int[] shards = ShardRouter.calculateShards(guildIds, 16);
        
        assertEquals(guildIds.length, shards.length);
        for (int i = 0; i < guildIds.length; i++) {
            assertEquals(ShardRouter.calculateShard(guildIds[i], 16), shards[i]);
        }
    }
}
