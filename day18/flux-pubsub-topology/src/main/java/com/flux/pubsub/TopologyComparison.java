package com.flux.pubsub;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Compares user-centric vs guild-centric topic routing performance.
 */
public class TopologyComparison {
    
    public record BenchmarkResult(
        String topology,
        long durationMs,
        long messagesPublished,
        long totalSubscribers,
        long memoryUsedMB,
        long throughputMsgPerSec
    ) {}
    
    /**
     * Benchmark user-centric routing: publish to user:{id} for each guild member.
     */
    public static BenchmarkResult benchmarkUserCentric(
        int guildCount, 
        int membersPerGuild, 
        int messagesPerGuild
    ) {
        var broker = new LocalPubSubBroker();
        var subscribers = new ArrayList<GatewaySubscriber>();
        var random = new Random();
        
        // Create subscribers (representing users)
        for (int i = 0; i < guildCount * membersPerGuild; i++) {
            var sub = new GatewaySubscriber("user_" + i, 1024, null);
            subscribers.add(sub);
            broker.subscribe("user:" + i, sub);
        }
        
        long startMemory = getUsedMemory();
        long startTime = System.nanoTime();
        long totalPublications = 0;
        
        // Simulate messages: each message requires N publications (one per member)
        for (int guild = 0; guild < guildCount; guild++) {
            for (int msg = 0; msg < messagesPerGuild; msg++) {
                byte[] message = ("Message " + msg + " in guild " + guild).getBytes(StandardCharsets.UTF_8);
                
                // User-centric: publish to EACH member's topic
                for (int member = 0; member < membersPerGuild; member++) {
                    int userId = guild * membersPerGuild + member;
                    broker.publish("user:" + userId, message);
                    totalPublications++;
                }
            }
        }
        
        long duration = (System.nanoTime() - startTime) / 1_000_000; // ms
        long memoryUsed = (getUsedMemory() - startMemory) / (1024 * 1024); // MB
        long throughput = totalPublications * 1000 / Math.max(duration, 1);
        
        return new BenchmarkResult(
            "user-centric",
            duration,
            totalPublications,
            subscribers.size(),
            memoryUsed,
            throughput
        );
    }
    
    /**
     * Benchmark guild-centric routing: publish once to guild:{id}.
     */
    public static BenchmarkResult benchmarkGuildCentric(
        int guildCount,
        int membersPerGuild,
        int messagesPerGuild
    ) {
        var broker = new LocalPubSubBroker();
        var subscribers = new ArrayList<GatewaySubscriber>();
        
        // Create subscribers and subscribe to guild topics
        for (int guild = 0; guild < guildCount; guild++) {
            String guildTopic = "guild:" + guild;
            
            for (int member = 0; member < membersPerGuild; member++) {
                int userId = guild * membersPerGuild + member;
                var sub = new GatewaySubscriber("user_" + userId, 1024, null);
                subscribers.add(sub);
                broker.subscribe(guildTopic, sub);
            }
        }
        
        long startMemory = getUsedMemory();
        long startTime = System.nanoTime();
        long totalPublications = 0;
        
        // Simulate messages: each message is ONE publication to guild topic
        for (int guild = 0; guild < guildCount; guild++) {
            String guildTopic = "guild:" + guild;
            
            for (int msg = 0; msg < messagesPerGuild; msg++) {
                byte[] message = ("Message " + msg + " in guild " + guild).getBytes(StandardCharsets.UTF_8);
                broker.publish(guildTopic, message);
                totalPublications++;
            }
        }
        
        long duration = (System.nanoTime() - startTime) / 1_000_000; // ms
        long memoryUsed = (getUsedMemory() - startMemory) / (1024 * 1024); // MB
        long throughput = totalPublications * 1000 / Math.max(duration, 1);
        
        return new BenchmarkResult(
            "guild-centric",
            duration,
            totalPublications,
            subscribers.size(),
            memoryUsed,
            throughput
        );
    }
    
    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
