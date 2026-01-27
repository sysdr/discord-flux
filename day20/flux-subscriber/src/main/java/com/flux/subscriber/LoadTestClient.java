package com.flux.subscriber;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.*;
import java.util.concurrent.*;

public class LoadTestClient {
    private final GatewayServer gateway;
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;

    public LoadTestClient(GatewayServer gateway, String redisUri) {
        this.gateway = gateway;
        this.redisClient = RedisClient.create(redisUri);
        this.connection = redisClient.connect();
        this.commands = connection.sync();
    }

    public void runSimulation(int numUsers, int numGuilds) throws InterruptedException {
        System.out.printf("\n🧪 Starting load test: %d users across %d guilds%n", numUsers, numGuilds);

        Random random = new Random();
        List<String> connectionIds = new ArrayList<>();

        // Phase 1: Users connect and join guilds
        System.out.println("Phase 1: Users joining guilds...");
        for (int i = 0; i < numUsers; i++) {
            String connId = "conn_" + i;
            connectionIds.add(connId);

            // Each user joins 3-5 random guilds
            int guildsToJoin = 3 + random.nextInt(3);
            for (int j = 0; j < guildsToJoin; j++) {
                String guildId = "guild_" + random.nextInt(numGuilds);
                gateway.getSubscriptionManager().addUserToGuild(guildId, connId);
            }

            if ((i + 1) % 100 == 0) {
                System.out.printf("  → %d users connected%n", i + 1);
            }
        }

        Thread.sleep(2000);
        System.out.printf("✓ Active subscriptions: %d%n", 
            gateway.getSubscriptionManager().getTotalSubscriptions());

        // Phase 2: Publish messages to guilds
        System.out.println("\nPhase 2: Publishing messages...");
        ExecutorService publisher = Executors.newVirtualThreadPerTaskExecutor();
        int totalMessages = 500;

        for (int i = 0; i < totalMessages; i++) {
            String guildId = "guild_" + random.nextInt(numGuilds);
            int msgNum = i;

            publisher.submit(() -> {
                Map<String, String> message = Map.of(
                    "guild_id", guildId,
                    "user_id", "user_" + msgNum,
                    "content", "Message #" + msgNum,
                    "timestamp", String.valueOf(System.currentTimeMillis())
                );

                commands.xadd("guild:stream:" + guildId, message);
            });

            if ((i + 1) % 100 == 0) {
                System.out.printf("  → %d messages published%n", i + 1);
            }
        }

        publisher.shutdown();
        publisher.awaitTermination(10, TimeUnit.SECONDS);

        Thread.sleep(3000); // Let messages propagate

        System.out.printf("✓ Messages delivered: %d%n", 
            gateway.getSubscriptionManager().getTotalMessagesDelivered());
        System.out.printf("✓ Unroutable messages: %d%n", 
            gateway.getSubscriptionManager().getUnroutableMessages());

        // Phase 3: Users disconnect
        System.out.println("\nPhase 3: Users disconnecting...");
        for (String connId : connectionIds) {
            // Remove user from all their guilds
            for (int j = 0; j < numGuilds; j++) {
                String guildId = "guild_" + j;
                gateway.getSubscriptionManager().removeUserFromGuild(guildId, connId);
            }
        }

        System.out.printf("✓ Users disconnected. Subscriptions will drain over 30 seconds...%n");
    }

    public void shutdown() {
        connection.close();
        redisClient.shutdown();
    }

    public static void main(String[] args) throws Exception {
        String redisUri = "redis://localhost:6379";
        GatewayServer gateway = new GatewayServer(redisUri, 8080);

        LoadTestClient client = new LoadTestClient(gateway, redisUri);

        try {
            client.runSimulation(100, 20);

            System.out.println("\n⏳ Monitoring for 60 seconds...");
            Thread.sleep(60000);

            System.out.printf("\nFinal metrics:%n");
            System.out.printf("  Subscriptions: %d%n", 
                gateway.getSubscriptionManager().getTotalSubscriptions());
            System.out.printf("  Churn count: %d%n", 
                gateway.getSubscriptionManager().getSubscriptionChurnCount());

        } finally {
            client.shutdown();
            gateway.shutdown();
        }
    }
}
