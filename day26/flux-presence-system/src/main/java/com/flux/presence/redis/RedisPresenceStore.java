package com.flux.presence.redis;

import com.flux.presence.core.PresenceStatus;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Redis-backed presence store (L2 cache).
 * Uses Lettuce async client for non-blocking I/O.
 */
public class RedisPresenceStore implements AutoCloseable {
    
    private static final Logger logger = Logger.getLogger(RedisPresenceStore.class.getName());
    private static final int PRESENCE_TTL_SECONDS = 90; // 30s grace period beyond 60s heartbeat
    
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> asyncCommands;
    
    // Metrics
    private final AtomicLong writeCount = new AtomicLong(0);
    private final AtomicLong readCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    public RedisPresenceStore(String host, int port) {
        RedisURI redisUri = RedisURI.builder()
            .withHost(host)
            .withPort(port)
            .withTimeout(Duration.ofSeconds(2))
            .build();
        
        this.client = RedisClient.create(redisUri);
        this.connection = client.connect();
        this.asyncCommands = connection.async();
        this.asyncCommands.setTimeout(Duration.ofMillis(500));
        
        logger.info("Redis connection established: " + host + ":" + port);
    }
    
    /**
     * Set user presence with TTL. Uses SETEX for atomicity.
     */
    public CompletableFuture<Void> setPresence(long userId, PresenceStatus status) {
        String key = buildKey(userId);
        writeCount.incrementAndGet();
        
        return asyncCommands.setex(key, PRESENCE_TTL_SECONDS, status.getValue())
            .toCompletableFuture()
            .thenApply(result -> (Void) null)
            .exceptionally(ex -> {
                errorCount.incrementAndGet();
                logger.warning("Failed to set presence for user " + userId + ": " + ex.getMessage());
                return (Void) null;
            });
    }
    
    /**
     * Get user presence from Redis.
     */
    public CompletableFuture<PresenceStatus> getPresence(long userId) {
        String key = buildKey(userId);
        readCount.incrementAndGet();
        
        return asyncCommands.get(key)
            .toCompletableFuture()
            .thenApply(value -> {
                if (value == null) {
                    return PresenceStatus.OFFLINE;
                }
                return PresenceStatus.fromString(value);
            })
            .exceptionally(ex -> {
                errorCount.incrementAndGet();
                logger.warning("Failed to get presence for user " + userId + ": " + ex.getMessage());
                return PresenceStatus.UNKNOWN;
            });
    }
    
    /**
     * Batch write presence updates using pipelining.
     */
    public CompletableFuture<Void> batchSetPresence(List<Long> userIds, PresenceStatus status) {
        if (userIds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        asyncCommands.setAutoFlushCommands(false); // Enable pipelining
        
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (long userId : userIds) {
            String key = buildKey(userId);
            futures.add(asyncCommands.setex(key, PRESENCE_TTL_SECONDS, status.getValue()).toCompletableFuture());
        }
        
        asyncCommands.flushCommands(); // Send all commands at once
        asyncCommands.setAutoFlushCommands(true);
        
        writeCount.addAndGet(userIds.size());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> (Void) null)
            .exceptionally(ex -> {
                errorCount.incrementAndGet();
                logger.warning("Batch write failed: " + ex.getMessage());
                return (Void) null;
            });
    }
    
    /**
     * Delete user presence (used for explicit logouts).
     */
    public CompletableFuture<Void> deletePresence(long userId) {
        String key = buildKey(userId);
        return asyncCommands.del(key)
            .toCompletableFuture()
            .thenApply(result -> (Void) null);
    }
    
    private String buildKey(long userId) {
        return "user:" + userId + ":presence";
    }
    
    public RedisStats getStats() {
        return new RedisStats(writeCount.get(), readCount.get(), errorCount.get());
    }
    
    public record RedisStats(long writes, long reads, long errors) {}
    
    @Override
    public void close() {
        connection.close();
        client.shutdown();
        logger.info("Redis connection closed");
    }
}
