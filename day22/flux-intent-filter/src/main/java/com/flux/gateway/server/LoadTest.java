package com.flux.gateway.server;

import com.flux.gateway.connection.GatewayConnection;
import com.flux.gateway.intent.GatewayIntent;
import com.flux.gateway.model.GatewayEvent;
import com.flux.gateway.router.IntentAwareRouter;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoadTest {
    public static void main(String[] args) {
        int connectionCount = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int eventRate = args.length > 1 ? Integer.parseInt(args[1]) : 10000;
        int durationSeconds = args.length > 2 ? Integer.parseInt(args[2]) : 60;
        
        System.out.println("🔥 Load Test Configuration:");
        System.out.println("   Connections: " + connectionCount);
        System.out.println("   Event Rate: " + eventRate + " events/sec");
        System.out.println("   Duration: " + durationSeconds + " seconds");
        System.out.println();
        
        var router = new IntentAwareRouter();
        var random = new Random();
        
        // Create connections with realistic intent distribution
        System.out.println("📡 Creating connections...");
        for (int i = 0; i < connectionCount; i++) {
            boolean isVerified = random.nextDouble() < 0.3; // 30% verified
            long intents = generateRandomIntents(random, i, connectionCount, isVerified);
            var conn = new GatewayConnection("load-user-" + i, intents, isVerified);
            router.registerConnection(conn);
            
            if ((i + 1) % 1000 == 0) {
                System.out.println("   Created " + (i + 1) + " connections...");
            }
        }
        System.out.println("✅ All connections created");
        
        // Start event generation
        System.out.println("🚀 Starting event generation...");
        var executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
        
        long startTime = System.currentTimeMillis();
        long eventsToGenerate = (long) eventRate * durationSeconds;
        long intervalNanos = TimeUnit.SECONDS.toNanos(1) / eventRate;
        
        executor.scheduleAtFixedRate(() -> {
            var event = generateRandomEvent(random);
            var targets = generateRandomTargets(random, connectionCount);
            router.dispatch(event, targets);
        }, 0, intervalNanos, TimeUnit.NANOSECONDS);
        
        // Monitor and report
        var monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            var metrics = router.getMetrics();
            long elapsed = System.currentTimeMillis() - startTime;
            double progress = (metrics.totalEventsProcessed() * 100.0) / eventsToGenerate;
            
            System.out.printf("[%.1fs] Progress: %.1f%% | Processed: %,d | Sent: %,d | Filtered: %,d (%.2f%%) | Bandwidth Saved: %.2f MB%n",
                elapsed / 1000.0,
                progress,
                metrics.totalEventsProcessed(),
                metrics.totalEventsSent(),
                metrics.totalEventsFiltered(),
                metrics.filterRate(),
                metrics.bandwidthSaved() / 1024.0 / 1024.0
            );
        }, 1, 1, TimeUnit.SECONDS);
        
        // Stop after duration
        executor.schedule(() -> {
            executor.shutdown();
            monitor.shutdown();
            
            var metrics = router.getMetrics();
            long totalTime = System.currentTimeMillis() - startTime;
            
            System.out.println();
            System.out.println("✅ Load Test Complete");
            System.out.println("═══════════════════════════════════════");
            System.out.printf("Total Time: %.2fs%n", totalTime / 1000.0);
            System.out.printf("Events Processed: %,d%n", metrics.totalEventsProcessed());
            System.out.printf("Events Sent: %,d%n", metrics.totalEventsSent());
            System.out.printf("Events Filtered: %,d (%.2f%%)%n", 
                metrics.totalEventsFiltered(), metrics.filterRate());
            System.out.printf("Bandwidth Saved: %.2f MB%n", 
                metrics.bandwidthSaved() / 1024.0 / 1024.0);
            System.out.printf("Avg Check Latency: %d ns%n", metrics.lastCheckNanos());
            System.out.printf("Throughput: %,d events/sec%n", 
                metrics.totalEventsProcessed() * 1000 / totalTime);
            System.out.println("═══════════════════════════════════════");
            
            System.exit(0);
        }, durationSeconds, TimeUnit.SECONDS);
    }
    
    private static long generateRandomIntents(Random random, int index, int total, boolean isVerified) {
        double r = (double) index / total;
        if (r < 0.4) return GatewayIntent.GUILD_MESSAGES.mask;
        if (r < 0.6) {
            if (!isVerified) return GatewayIntent.combine(GatewayIntent.GUILDS, GatewayIntent.GUILD_VOICE_STATES);
            return GatewayIntent.combine(GatewayIntent.GUILDS, GatewayIntent.GUILD_PRESENCES);
        }
        if (r < 0.75) return GatewayIntent.combine(GatewayIntent.GUILDS, GatewayIntent.GUILD_VOICE_STATES);
        if (r < 0.9) return GatewayIntent.combine(GatewayIntent.GUILDS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS);
        if (!isVerified) return GatewayIntent.combine(GatewayIntent.GUILDS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS);
        return GatewayIntent.combine(GatewayIntent.GUILDS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS);
    }
    
    private static GatewayEvent generateRandomEvent(Random random) {
        return switch (random.nextInt(6)) {
            case 0 -> GatewayEvent.messageCreate("guild-" + random.nextInt(100), "Test message");
            case 1 -> GatewayEvent.presenceUpdate("user-" + random.nextInt(10000), "online");
            case 2 -> GatewayEvent.typingStart("guild-" + random.nextInt(100), "user-" + random.nextInt(10000));
            case 3 -> GatewayEvent.voiceStateUpdate("guild-" + random.nextInt(100), "voice-1");
            case 4 -> GatewayEvent.reactionAdd("msg-" + random.nextInt(10000), "👍");
            case 5 -> GatewayEvent.guildMemberAdd("guild-" + random.nextInt(100), "user-" + random.nextInt(10000));
            default -> throw new IllegalStateException();
        };
    }
    
    private static Set<String> generateRandomTargets(Random random, int maxUsers) {
        int targetCount = 10 + random.nextInt(40);
        var set = new java.util.HashSet<String>();
        while (set.size() < Math.min(targetCount, maxUsers)) {
            set.add("load-user-" + random.nextInt(maxUsers));
        }
        return set;
    }
}
