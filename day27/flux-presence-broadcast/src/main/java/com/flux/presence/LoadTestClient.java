package com.flux.presence;

import java.util.Random;
import java.util.concurrent.*;

/**
 * Load test client that simulates N users with random presence updates.
 */
public class LoadTestClient {
    
    public static void main(String[] args) throws Exception {
        int userCount = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        int updateRate = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        int durationSec = args.length > 2 ? Integer.parseInt(args[2]) : 60;
        
        System.out.println("=== Flux Presence Load Test ===");
        System.out.println("Users: " + userCount);
        System.out.println("Update Rate: " + updateRate + " updates/sec");
        System.out.println("Duration: " + durationSec + " seconds");
        System.out.println();
        
        GuildMemberRegistry registry = new GuildMemberRegistry();
        PresenceBroadcaster broadcaster = new PresenceBroadcaster(registry);
        
        long guildId = 1000L;
        Random random = new Random();
        
        // Create users
        ConcurrentHashMap<Long, GatewayConnection> connections = new ConcurrentHashMap<>();
        System.out.print("Creating " + userCount + " connections...");
        for (long i = 0; i < userCount; i++) {
            GatewayConnection conn = new GatewayConnection(i, 1024);
            connections.put(i, conn);
            registry.addMember(guildId, conn);
        }
        System.out.println(" ✓");
        
        // Start load test
        System.out.println("Starting load test...");
        long startTime = System.currentTimeMillis();
        
        ScheduledExecutorService updateExecutor = Executors.newScheduledThreadPool(4);
        
        long delayMs = 1000 / updateRate;
        
        updateExecutor.scheduleAtFixedRate(() -> {
            try {
                long userId = random.nextInt(userCount);
                GatewayConnection conn = connections.get(userId);
                
                PresenceStatus[] statuses = PresenceStatus.values();
                PresenceStatus newStatus = statuses[random.nextInt(statuses.length)];
                
                conn.setCurrentStatus(newStatus);
                
                PresenceUpdate update = new PresenceUpdate(
                    userId,
                    newStatus,
                    System.currentTimeMillis(),
                    "Activity" + random.nextInt(100)
                );
                
                broadcaster.schedulePresenceUpdate(guildId, update);
                
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }, 0, delayMs, TimeUnit.MILLISECONDS);
        
        // Metrics reporting
        ScheduledExecutorService metricsExecutor = Executors.newScheduledThreadPool(1);
        metricsExecutor.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            long broadcasts = broadcaster.getBroadcastCount();
            long messages = broadcaster.getMessagesSent();
            long slowConsumers = broadcaster.getSlowConsumerDetections();
            
            double messagesPerSec = messages / (double) elapsed;
            double broadcastsPerSec = broadcasts / (double) elapsed;
            
            long totalDropped = connections.values().stream()
                .mapToLong(c -> c.getRingBuffer().getDroppedCount())
                .sum();
            
            System.out.printf("[%ds] Broadcasts: %d (%.1f/s) | Messages: %d (%.0f/s) | Dropped: %d | Slow: %d%n",
                elapsed, broadcasts, broadcastsPerSec, messages, messagesPerSec, totalDropped, slowConsumers);
            
        }, 5, 5, TimeUnit.SECONDS);
        
        // Run for specified duration
        TimeUnit.SECONDS.sleep(durationSec);
        
        // Shutdown
        System.out.println("\nShutting down...");
        updateExecutor.shutdown();
        metricsExecutor.shutdown();
        broadcaster.shutdown();
        
        long totalTime = (System.currentTimeMillis() - startTime) / 1000;
        long totalBroadcasts = broadcaster.getBroadcastCount();
        long totalMessages = broadcaster.getMessagesSent();
        
        System.out.println("\n=== Load Test Complete ===");
        System.out.println("Total Time: " + totalTime + "s");
        System.out.println("Total Broadcasts: " + totalBroadcasts);
        System.out.println("Total Messages: " + totalMessages);
        System.out.println("Avg Broadcast Rate: " + (totalBroadcasts / totalTime) + " broadcasts/sec");
        System.out.println("Avg Message Rate: " + (totalMessages / totalTime) + " messages/sec");
        System.out.println("Avg Fan-out: " + (totalMessages / (double) totalBroadcasts) + " recipients/broadcast");
    }
}
