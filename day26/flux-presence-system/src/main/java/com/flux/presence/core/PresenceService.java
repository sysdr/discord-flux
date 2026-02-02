package com.flux.presence.core;

import com.flux.presence.cache.LocalPresenceCache;
import com.flux.presence.redis.RedisPresenceStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Main presence service with hybrid L1/L2 caching and write batching.
 */
public class PresenceService implements AutoCloseable {
    
    private static final Logger logger = Logger.getLogger(PresenceService.class.getName());
    private static final long L1_CACHE_TTL_MILLIS = 5000; // 5 seconds
    private static final int BATCH_FLUSH_INTERVAL_MS = 500;
    private static final int MAX_BATCH_SIZE = 1000;
    
    private final LocalPresenceCache localCache;
    private final RedisPresenceStore redisStore;
    private final ConcurrentLinkedQueue<Long> pendingUpdates;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = true;
    
    public PresenceService(String redisHost, int redisPort) {
        this.localCache = new LocalPresenceCache(L1_CACHE_TTL_MILLIS);
        this.redisStore = new RedisPresenceStore(redisHost, redisPort);
        this.pendingUpdates = new ConcurrentLinkedQueue<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Start background batch flusher
        scheduler.scheduleAtFixedRate(
            this::flushPendingUpdates,
            BATCH_FLUSH_INTERVAL_MS,
            BATCH_FLUSH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        logger.info("Presence service started with L1 cache TTL: " + L1_CACHE_TTL_MILLIS + "ms");
    }
    
    /**
     * Mark user as online. Queues update for batching.
     */
    public void markOnline(long userId) {
        // Update L1 cache immediately for local reads
        localCache.put(userId, PresenceStatus.ONLINE);
        
        // Queue for Redis batch write
        pendingUpdates.offer(userId);
    }
    
    /**
     * Get user presence. Checks L1 cache first, falls back to Redis.
     */
    public CompletableFuture<PresenceStatus> getPresence(long userId) {
        // Check L1 cache
        PresenceStatus cached = localCache.get(userId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        
        // L1 miss - fetch from Redis and populate cache
        return redisStore.getPresence(userId)
            .thenApply(status -> {
                localCache.put(userId, status);
                return status;
            });
    }
    
    /**
     * Mark user as offline (explicit logout).
     */
    public CompletableFuture<Void> markOffline(long userId) {
        localCache.invalidate(userId);
        return redisStore.setPresence(userId, PresenceStatus.OFFLINE);
    }
    
    /**
     * Background task: Flush pending updates to Redis in batches.
     */
    private void flushPendingUpdates() {
        if (!running) return;
        
        List<Long> batch = new ArrayList<>();
        Long userId;
        while ((userId = pendingUpdates.poll()) != null) {
            batch.add(userId);
            if (batch.size() >= MAX_BATCH_SIZE) break;
        }
        
        if (!batch.isEmpty()) {
            redisStore.batchSetPresence(batch, PresenceStatus.ONLINE)
                .exceptionally(ex -> {
                    logger.warning("Batch flush failed: " + ex.getMessage());
                    return null;
                });
        }
    }
    
    /**
     * Get service metrics.
     */
    public ServiceMetrics getMetrics() {
        var cacheStats = localCache.getStats();
        var redisStats = redisStore.getStats();
        return new ServiceMetrics(
            cacheStats.hits(),
            cacheStats.misses(),
            cacheStats.hitRate(),
            redisStats.writes(),
            redisStats.reads(),
            redisStats.errors(),
            pendingUpdates.size()
        );
    }
    
    public record ServiceMetrics(
        long cacheHits,
        long cacheMisses,
        double cacheHitRate,
        long redisWrites,
        long redisReads,
        long redisErrors,
        int pendingQueueSize
    ) {}
    
    @Override
    public void close() {
        running = false;
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Final flush
        flushPendingUpdates();
        
        redisStore.close();
        logger.info("Presence service stopped");
    }
}
