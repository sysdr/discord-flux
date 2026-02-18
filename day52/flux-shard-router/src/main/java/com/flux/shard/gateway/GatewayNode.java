package com.flux.shard.gateway;

import com.flux.shard.router.ShardRouter;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a Gateway instance that owns a range of shards.
 * Uses VarHandle for lock-free atomic operations.
 */
public class GatewayNode {
    
    private static final VarHandle EVENT_COUNT;
    
    static {
        try {
            EVENT_COUNT = MethodHandles.lookup()
                .findVarHandle(GatewayNode.class, "eventCount", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private final String nodeId;
    private final int shardStart;
    private final int shardEnd;
    private final int totalShards;
    
    private volatile long eventCount = 0L;
    
    public GatewayNode(String nodeId, int shardStart, int shardEnd, int totalShards) {
        this.nodeId = nodeId;
        this.shardStart = shardStart;
        this.shardEnd = shardEnd;
        this.totalShards = totalShards;
    }
    
    /**
     * Check if this node should handle the given guild
     */
    public boolean owns(long guildId) {
        return ShardRouter.belongsToInstance(guildId, totalShards, shardStart, shardEnd);
    }
    
    /**
     * Process an event for a guild (lock-free increment)
     */
    public void handleEvent(long guildId) {
        if (!owns(guildId)) {
            throw new IllegalArgumentException(
                "Guild " + guildId + " does not belong to " + nodeId
            );
        }
        EVENT_COUNT.getAndAdd(this, 1L);
    }
    
    public long getEventCount() {
        return (long) EVENT_COUNT.getVolatile(this);
    }
    
    public String getNodeId() { return nodeId; }
    public int getShardStart() { return shardStart; }
    public int getShardEnd() { return shardEnd; }
    public int getShardCount() { return shardEnd - shardStart; }
    
    @Override
    public String toString() {
        return String.format("%s [shards %d-%d, events=%d]", 
            nodeId, shardStart, shardEnd - 1, getEventCount());
    }
}
