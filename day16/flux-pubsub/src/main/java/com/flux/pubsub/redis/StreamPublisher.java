package com.flux.pubsub.redis;

import com.flux.pubsub.core.GuildEvent;
import com.flux.pubsub.metrics.MetricsCollector;
import io.lettuce.core.RedisClient;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class StreamPublisher {
    private static final Logger log = LoggerFactory.getLogger(StreamPublisher.class);
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> async;
    private final MetricsCollector metrics;

    public StreamPublisher(String redisUri, MetricsCollector metrics) {
        this.redisClient = RedisClient.create(redisUri);
        this.connection = redisClient.connect();
        this.async = connection.async();
        this.metrics = metrics;
        log.info("StreamPublisher initialized with Redis: {}", redisUri);
    }

    // Publish event to Redis Stream (async, non-blocking)
    public CompletableFuture<String> publish(GuildEvent event) {
        String streamKey = "guild:" + event.guildId() + ":events";
        long startNanos = System.nanoTime();
        
        return async.xadd(streamKey, event.toMap())
            .toCompletableFuture()
            .whenComplete((messageId, error) -> {
                long latencyNanos = System.nanoTime() - startNanos;
                if (error != null) {
                    log.error("Failed to publish event to {}", streamKey, error);
                    metrics.recordPublishError();
                } else {
                    metrics.recordPublish(latencyNanos);
                    log.debug("Published message {} to {}", messageId, streamKey);
                }
            });
    }

    // Publish with maxlen to prevent unbounded stream growth
    public CompletableFuture<String> publishWithTrim(GuildEvent event, long maxLen) {
        String streamKey = "guild:" + event.guildId() + ":events";
        XAddArgs args = XAddArgs.Builder.maxlen(maxLen).approximateTrimming();
        
        return async.xadd(streamKey, args, event.toMap())
            .toCompletableFuture();
    }

    public void close() {
        connection.close();
        redisClient.shutdown();
        log.info("StreamPublisher closed");
    }
}
