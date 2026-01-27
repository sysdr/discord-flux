package com.flux.subscriber;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RedisStreamSubscriber {
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> syncCommands;
    private final ConcurrentHashMap<String, Thread> activeSubscriptions = new ConcurrentHashMap<>();
    private final SubscriptionManager subscriptionManager;
    private volatile boolean running = true;

    public RedisStreamSubscriber(String redisUri, SubscriptionManager subscriptionManager) {
        this.redisClient = RedisClient.create(redisUri);
        this.connection = redisClient.connect();
        this.syncCommands = connection.sync();
        this.subscriptionManager = subscriptionManager;
    }

    public void subscribe(String guildId) {
        String streamKey = "guild:stream:" + guildId;
        
        Thread subscriptionThread = Thread.startVirtualThread(() -> {
            String lastId = "$"; // Only new messages
            
            while (running && activeSubscriptions.containsKey(guildId)) {
                try {
                    // XREAD BLOCK 5000 STREAMS streamKey lastId
                    List<StreamMessage<String, String>> messages = syncCommands.xread(
                        XReadArgs.Builder.block(5000).count(100),
                        XReadArgs.StreamOffset.from(streamKey, lastId)
                    );
                    
                    if (messages != null && !messages.isEmpty()) {
                        for (StreamMessage<String, String> streamMsg : messages) {
                            Map<String, String> body = streamMsg.getBody();
                            Message msg = Message.fromRedisMap(body);
                            subscriptionManager.dispatchMessage(guildId, msg);
                            lastId = streamMsg.getId();
                        }
                    }
                } catch (Exception e) {
                    if (running) {
                        System.err.printf("[RedisStreamSubscriber] Error reading guild %s: %s%n", 
                            guildId, e.getMessage());
                    }
                }
            }
            
            System.out.printf("[RedisStreamSubscriber] Stopped subscription for guild %s%n", guildId);
        });
        
        activeSubscriptions.put(guildId, subscriptionThread);
    }

    public void unsubscribe(String guildId) {
        Thread thread = activeSubscriptions.remove(guildId);
        if (thread != null) {
            thread.interrupt();
        }
    }

    public void shutdown() {
        running = false;
        activeSubscriptions.values().forEach(Thread::interrupt);
        connection.close();
        redisClient.shutdown();
    }
}
