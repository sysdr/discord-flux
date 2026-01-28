package com.flux.gateway.server;

import com.flux.gateway.connection.GatewayConnection;
import com.flux.gateway.intent.GatewayIntent;
import com.flux.gateway.model.GatewayEvent;
import com.flux.gateway.router.IntentAwareRouter;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GatewayServer {
    private final IntentAwareRouter router;
    private final DashboardServer dashboard;
    private final ScheduledExecutorService scheduler;
    private final Random random = new Random();

    public GatewayServer(int dashboardPort) throws Exception {
        this.router = new IntentAwareRouter();
        this.dashboard = new DashboardServer(router, dashboardPort);
        this.scheduler = Executors.newScheduledThreadPool(4);
    }

    public void start() {
        System.out.println("🚀 Starting Flux Gateway - Intent Filter System");
        
        // Start dashboard
        dashboard.start();
        
        // Initialize connections with various intent patterns
        initializeConnections();
        
        // Start event generators
        startEventGenerators();
        
        System.out.println("✅ Gateway is running");
        System.out.println("📊 Dashboard: http://localhost:8081/dashboard");
        System.out.println("📈 Metrics API: http://localhost:8081/metrics");
    }

    private void initializeConnections() {
        System.out.println("📡 Initializing connections...");
        
        // Pattern 1: Message-only bots (40%)
        for (int i = 0; i < 40; i++) {
            long intents = GatewayIntent.GUILD_MESSAGES.mask;
            var conn = new GatewayConnection("user-" + i, intents, false);
            router.registerConnection(conn);
        }
        
        // Pattern 2: Presence tracking (20%)
        for (int i = 40; i < 60; i++) {
            long intents = GatewayIntent.combine(
                GatewayIntent.GUILDS,
                GatewayIntent.GUILD_PRESENCES
            );
            var conn = new GatewayConnection("user-" + i, intents, true);
            router.registerConnection(conn);
        }
        
        // Pattern 3: Voice bots (15%)
        for (int i = 60; i < 75; i++) {
            long intents = GatewayIntent.combine(
                GatewayIntent.GUILDS,
                GatewayIntent.GUILD_VOICE_STATES
            );
            var conn = new GatewayConnection("user-" + i, intents, false);
            router.registerConnection(conn);
        }
        
        // Pattern 4: Moderation bots (15%)
        for (int i = 75; i < 90; i++) {
            long intents = GatewayIntent.combine(
                GatewayIntent.GUILDS,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MODERATION
            );
            var conn = new GatewayConnection("user-" + i, intents, false);
            router.registerConnection(conn);
        }
        
        // Pattern 5: Full-featured clients (10%)
        for (int i = 90; i < 100; i++) {
            long intents = GatewayIntent.combine(
                GatewayIntent.GUILDS,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_PRESENCES,
                GatewayIntent.GUILD_VOICE_STATES,
                GatewayIntent.GUILD_MESSAGE_REACTIONS,
                GatewayIntent.MESSAGE_CONTENT
            );
            var conn = new GatewayConnection("user-" + i, intents, true);
            router.registerConnection(conn);
        }
        
        System.out.println("✅ Registered " + router.getConnectionCount() + " connections");
    }

    private void startEventGenerators() {
        // Message creates (high volume)
        scheduler.scheduleAtFixedRate(() -> {
            var event = GatewayEvent.messageCreate("guild-123", "Hello world!");
            var targets = randomUserSet(50);
            router.dispatch(event, targets);
        }, 0, 100, TimeUnit.MILLISECONDS);
        
        // Presence updates (moderate volume)
        scheduler.scheduleAtFixedRate(() -> {
            String[] statuses = {"online", "idle", "dnd", "offline"};
            var event = GatewayEvent.presenceUpdate(
                "user-" + random.nextInt(100),
                statuses[random.nextInt(statuses.length)]
            );
            router.broadcast(event);
        }, 0, 200, TimeUnit.MILLISECONDS);
        
        // Typing indicators (high frequency, low payload)
        scheduler.scheduleAtFixedRate(() -> {
            var event = GatewayEvent.typingStart("guild-123", "user-" + random.nextInt(100));
            var targets = randomUserSet(30);
            router.dispatch(event, targets);
        }, 0, 50, TimeUnit.MILLISECONDS);
        
        // Voice state updates (sporadic)
        scheduler.scheduleAtFixedRate(() -> {
            var event = GatewayEvent.voiceStateUpdate("guild-123", "voice-channel-1");
            var targets = randomUserSet(20);
            router.dispatch(event, targets);
        }, 0, 500, TimeUnit.MILLISECONDS);
        
        // Reaction adds (moderate)
        scheduler.scheduleAtFixedRate(() -> {
            var event = GatewayEvent.reactionAdd("msg-" + random.nextInt(1000), "👍");
            var targets = randomUserSet(40);
            router.dispatch(event, targets);
        }, 0, 300, TimeUnit.MILLISECONDS);
        
        // Member joins (low frequency)
        scheduler.scheduleAtFixedRate(() -> {
            var event = GatewayEvent.guildMemberAdd("guild-123", "user-" + random.nextInt(10000));
            router.broadcast(event);
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private Set<String> randomUserSet(int count) {
        var set = new java.util.HashSet<String>();
        while (set.size() < Math.min(count, 100)) {
            set.add("user-" + random.nextInt(100));
        }
        return set;
    }

    public void stop() {
        scheduler.shutdown();
        dashboard.stop();
        System.out.println("Gateway stopped");
    }

    public static void main(String[] args) throws Exception {
        int dashboardPort = args.length > 0 ? Integer.parseInt(args[0]) : 8081;
        
        var server = new GatewayServer(dashboardPort);
        server.start();
        
        // Keep running
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }
}
