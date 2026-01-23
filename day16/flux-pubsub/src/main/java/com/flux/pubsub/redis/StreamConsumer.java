package com.flux.pubsub.redis;

import com.flux.pubsub.core.GuildEvent;
import com.flux.pubsub.metrics.MetricsCollector;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.lettuce.core.XReadArgs.StreamOffset.lastConsumed;

public class StreamConsumer {
    private static final Logger log = LoggerFactory.getLogger(StreamConsumer.class);
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> sync;
    private final String consumerGroup;
    private final String consumerId;
    private final MetricsCollector metrics;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Thread> subscriberThreads = new ArrayList<>();

    public StreamConsumer(String redisUri, String consumerGroup, String consumerId, MetricsCollector metrics) {
        this.redisClient = RedisClient.create(redisUri);
        this.connection = redisClient.connect();
        this.sync = connection.sync();
        this.consumerGroup = consumerGroup;
        this.consumerId = consumerId;
        this.metrics = metrics;
        log.info("StreamConsumer initialized: group={}, id={}", consumerGroup, consumerId);
    }

    // Subscribe to a Guild's event stream
    public void subscribe(long guildId, java.util.function.Consumer<GuildEvent> eventHandler) {
        String streamKey = "guild:" + guildId + ":events";
        
        // Create consumer group if not exists (will be created when stream exists)
        Runnable ensureConsumerGroup = () -> {
            try {
                // Check if stream exists first
                Long streamLength = sync.xlen(streamKey);
                if (streamLength != null && streamLength >= 0) {
                    // Stream exists, try to create consumer group
                    try {
                        sync.xgroupCreate(
                            XReadArgs.StreamOffset.from(streamKey, "0"),
                            consumerGroup
                        );
                        log.info("Created consumer group '{}' for stream '{}'", consumerGroup, streamKey);
                    } catch (Exception e) {
                        // Group already exists, ignore
                        if (!e.getMessage().contains("BUSYGROUP")) {
                            log.debug("Consumer group '{}' already exists for '{}'", consumerGroup, streamKey);
                        }
                    }
                }
            } catch (Exception e) {
                // Stream doesn't exist yet, will retry
                log.debug("Stream '{}' does not exist yet, will retry", streamKey);
            }
        };

        // Start Virtual Thread for this subscription
        Thread subscriberThread = Thread.startVirtualThread(() -> {
            log.info("Starting subscriber for guild {} on thread {}", guildId, Thread.currentThread());
            running.set(true);
            
            // Ensure consumer group exists before starting
            ensureConsumerGroup.run();
            
            while (running.get()) {
                try {
                    // Block for up to 5 seconds waiting for messages
                    List<StreamMessage<String, String>> messages = sync.xreadgroup(
                        Consumer.from(consumerGroup, consumerId),
                        XReadArgs.Builder.block(Duration.ofSeconds(5)),
                        lastConsumed(streamKey)
                    );

                    if (messages == null || messages.isEmpty()) {
                        continue;
                    }

                    // Process batch
                    for (StreamMessage<String, String> msg : messages) {
                        long startNanos = System.nanoTime();
                        try {
                            GuildEvent event = GuildEvent.fromMap(msg.getBody());
                            eventHandler.accept(event);
                            
                            // ACK message
                            sync.xack(streamKey, consumerGroup, msg.getId());
                            
                            long latencyNanos = System.nanoTime() - startNanos;
                            metrics.recordConsume(latencyNanos);
                            
                        } catch (Exception e) {
                            log.error("Error processing message {}", msg.getId(), e);
                            metrics.recordConsumeError();
                        }
                    }

                    metrics.recordBatchSize(messages.size());
                    
                } catch (Exception e) {
                    if (running.get()) {
                        // If it's a NOGROUP error, try to create the consumer group
                        if (e.getMessage() != null && e.getMessage().contains("NOGROUP")) {
                            log.debug("Consumer group not found, attempting to create it");
                            ensureConsumerGroup.run();
                            try {
                                Thread.sleep(1000); // Backoff before retry
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        } else {
                            log.error("Stream read error for guild {}", guildId, e);
                            try {
                                Thread.sleep(1000); // Backoff
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
            }
            
            log.info("Subscriber stopped for guild {}", guildId);
        });

        subscriberThreads.add(subscriberThread);
    }

    // Get pending message count (lag metric)
    public long getPendingCount(long guildId) {
        String streamKey = "guild:" + guildId + ":events";
        try {
            var pending = sync.xpending(streamKey, consumerGroup);
            return pending.getCount();
        } catch (Exception e) {
            return 0;
        }
    }

    public void stop() {
        running.set(false);
        subscriberThreads.forEach(Thread::interrupt);
        log.info("StreamConsumer stopped");
    }

    public void close() {
        stop();
        connection.close();
        redisClient.shutdown();
        log.info("StreamConsumer closed");
    }
}
