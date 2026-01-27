package com.flux.publisher.redis;

import com.flux.publisher.Message;
import com.flux.publisher.metrics.MetricsCollector;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Async Redis publisher using Lettuce client.
 * Uses Redis Streams (XADD) for persistent message queuing.
 * 
 * Why Lettuce over Jedis?
 * - Netty-based async I/O (no thread blocking)
 * - Connection pooling built-in
 * - Reactive API support
 */
public class RedisPublisher {
    private static final Logger log = LoggerFactory.getLogger(RedisPublisher.class);
    
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> async;
    private final MetricsCollector metrics;

    public RedisPublisher(String redisUrl, MetricsCollector metrics) {
        this.metrics = metrics;
        
        // Parse Redis URL
        RedisURI uri = RedisURI.create(redisUrl);
        uri.setTimeout(Duration.ofSeconds(5));
        
        // Create client with connection pooling
        this.client = RedisClient.create(uri);
        this.connection = client.connect();
        this.async = connection.async();
        
        // Disable auto-flush for better batching
        this.async.setAutoFlushCommands(false);
        
        log.info("Redis publisher connected: {}", redisUrl);
    }

    /**
     * Publish message to Redis Stream asynchronously.
     * Returns CompletableFuture that completes when Redis acknowledges.
     * 
     * Stream key pattern: guild:{guild_id}:messages
     * This enables guild-centric routing for Gateway subscribers.
     */
    public CompletableFuture<String> publish(Message message) {
        long startTime = System.nanoTime();
        
        String streamKey = message.getStreamKey();
        
        // XADD command: Add entry to stream
        // Returns the entry ID (timestamp-sequence)
        RedisFuture<String> future = async.xadd(
            streamKey,
            message.toRedisFields()
        );
        
        // Flush commands to Redis (batched for efficiency)
        async.flushCommands();
        
        // Convert Lettuce RedisFuture to CompletableFuture
        return future.toCompletableFuture()
            .whenComplete((id, ex) -> {
                long latencyNanos = System.nanoTime() - startTime;
                if (ex != null) {
                    log.error("Failed to publish message to {}: {}", streamKey, ex.getMessage());
                    metrics.recordPublishError(message.guildId());
                } else {
                    metrics.recordPublishSuccess(message.guildId(), latencyNanos);
                    log.debug("Published message to {}: {}", streamKey, id);
                }
            });
    }

    /**
     * Get connection statistics for monitoring.
     */
    public ConnectionStats getStats() {
        return new ConnectionStats(
            connection.isOpen(),
            async.isOpen()
        );
    }

    public void shutdown() {
        connection.close();
        client.shutdown();
        log.info("Redis publisher shut down");
    }

    public record ConnectionStats(boolean connectionOpen, boolean asyncOpen) {}
}
