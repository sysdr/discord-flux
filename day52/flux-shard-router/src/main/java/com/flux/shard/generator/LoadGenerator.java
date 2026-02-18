package com.flux.shard.generator;

import com.flux.shard.gateway.ShardDistributionTracker;
import com.flux.shard.router.ShardRouter;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-throughput load generator using virtual threads.
 */
public class LoadGenerator {
    
    private final ShardDistributionTracker tracker;
    private final int totalShards;
    private final AtomicLong eventsGenerated = new AtomicLong();
    
    public LoadGenerator(ShardDistributionTracker tracker, int totalShards) {
        this.tracker = tracker;
        this.totalShards = totalShards;
    }
    
    /**
     * Generate load with realistic Snowflake IDs
     */
    public void generateRealisticLoad(int numGuilds, int eventsPerGuild) {
        System.out.println("Generating load: " + numGuilds + " guilds, " + 
                          eventsPerGuild + " events each");
        
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var generator = new SnowflakeGenerator(1, 1);
        
        // Generate guild IDs with temporal spread
        long[] guildIds = new long[numGuilds];
        for (int i = 0; i < numGuilds; i++) {
            // Spread guilds across a 30-day time window
            long offset = (long) (Math.random() * 30L * 24 * 60 * 60 * 1000);
            guildIds[i] = generator.generateWithOffset(offset);
        }
        
        // Spawn virtual threads to simulate concurrent events
        for (long guildId : guildIds) {
            executor.submit(() -> {
                for (int i = 0; i < eventsPerGuild; i++) {
                    tracker.recordEvent(guildId);
                    eventsGenerated.incrementAndGet();
                    
                    // Small delay to simulate realistic event rate
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Load generation complete: " + 
            eventsGenerated.get() + " events processed");
    }
    
    /**
     * Generate continuous load for stress testing
     */
    public void generateContinuousLoad(int threadsCount, long durationSeconds) {
        System.out.println("Starting continuous load: " + threadsCount + 
                          " threads for " + durationSeconds + "s");
        
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var deadline = System.currentTimeMillis() + (durationSeconds * 1000);
        
        for (int i = 0; i < threadsCount; i++) {
            executor.submit(() -> {
                var generator = new SnowflakeGenerator(
                    Thread.currentThread().threadId() % 32, 1
                );
                var random = new Random();
                
                while (System.currentTimeMillis() < deadline) {
                    long guildId = generator.nextId();
                    tracker.recordEvent(guildId);
                    eventsGenerated.incrementAndGet();
                    
                    // Simulate bursty traffic
                    if (random.nextDouble() < 0.1) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(durationSeconds + 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Continuous load complete: " + 
            eventsGenerated.get() + " events @ " + 
            (eventsGenerated.get() / durationSeconds) + " events/sec");
    }
}
