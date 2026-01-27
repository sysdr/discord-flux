package com.flux.gateway;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class RedisSubscriber {
    private final String redisHost;
    private final int redisPort;
    private final String channel;
    private final BroadcastEngine broadcastEngine;
    private volatile boolean running;
    
    public RedisSubscriber(
        String redisHost, 
        int redisPort, 
        String channel, 
        BroadcastEngine broadcastEngine
    ) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.channel = channel;
        this.broadcastEngine = broadcastEngine;
        this.running = false;
    }
    
    /**
     * Start subscribing to Redis pub/sub in a Virtual Thread.
     */
    public void start() {
        running = true;
        
        Thread.startVirtualThread(() -> {
            System.out.println("[REDIS] Starting subscriber for channel: " + channel);
            
            try (Jedis jedis = new Jedis(redisHost, redisPort)) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        handleMessage(message);
                    }
                    
                    @Override
                    public void onSubscribe(String channel, int subscribedChannels) {
                        System.out.println("[REDIS] Subscribed to: " + channel);
                    }
                }, channel);
            } catch (Exception e) {
                System.err.println("[REDIS] Subscriber error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Parse and broadcast a Redis message.
     * Expected format: {"guildId":"guild_001","userId":"user_123","content":"Hello"}
     */
    private void handleMessage(String json) {
        try {
            // Simple JSON parsing (production would use Jackson or Gson)
            String guildId = extractJsonField(json, "guildId");
            String userId = extractJsonField(json, "userId");
            String content = extractJsonField(json, "content");
            
            GuildMessage message = new GuildMessage(guildId, userId, content);
            broadcastEngine.fanOut(new GuildId(guildId), message);
            
        } catch (Exception e) {
            System.err.println("[REDIS] Failed to parse message: " + json);
            e.printStackTrace();
        }
    }
    
    /**
     * Extract a field value from a simple JSON string.
     */
    private String extractJsonField(String json, String field) {
        String searchKey = "\"" + field + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            throw new IllegalArgumentException("Field not found: " + field);
        }
        
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        
        return json.substring(startIndex, endIndex);
    }
    
    public void stop() {
        running = false;
    }
}
