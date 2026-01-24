package com.flux.gateway;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Load test: Spawns WebSocket clients and publishes messages via Redis.
 */
public class LoadTest {
    private static final String WS_HOST = "localhost";
    private static final int WS_PORT = 9090;
    private static final String REDIS_URL = "redis://localhost:6379";
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    public static void main(String[] args) throws Exception {
        int guilds = 5;
        int clientsPerGuild = 20;
        int messagesPerGuild = 200;
        
        System.out.println("🧪 Starting Load Test");
        System.out.println("Guilds: " + guilds);
        System.out.println("Clients per guild: " + clientsPerGuild);
        System.out.println("Messages per guild: " + messagesPerGuild);
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch clientsReady = new CountDownLatch(guilds * clientsPerGuild);
        
        // Spawn WebSocket clients
        for (int g = 0; g < guilds; g++) {
            String guildId = "guild" + g;
            
            for (int c = 0; c < clientsPerGuild; c++) {
                int clientId = c;
                executor.submit(() -> {
                    try {
                        connectWebSocket(guildId, "client" + clientId, clientsReady);
                    } catch (Exception e) {
                        System.err.println("Client error: " + e.getMessage());
                    }
                });
            }
        }
        
        // Wait for all clients to connect
        System.out.println("⏳ Waiting for clients to connect...");
        clientsReady.await(30, TimeUnit.SECONDS);
        Thread.sleep(2000); // Let connections stabilize
        
        System.out.println("✅ All clients connected");
        
        // Publish messages via Redis
        RedisClient redisClient = RedisClient.create(REDIS_URL);
        StatefulRedisConnection<String, String> connection = redisClient.connect();
        RedisCommands<String, String> redis = connection.sync();
        
        System.out.println("📤 Publishing messages...");
        long start = System.currentTimeMillis();
        
        for (int g = 0; g < guilds; g++) {
            String guildId = "guild" + g;
            String streamKey = "guild:" + guildId + ":messages";
            
            for (int m = 0; m < messagesPerGuild; m++) {
                Map<String, String> message = Map.of(
                    "data", String.format("{\"guild\":\"%s\",\"msg\":%d,\"ts\":%d}", 
                        guildId, m, System.currentTimeMillis())
                );
                redis.xadd(streamKey, message);
            }
        }
        
        long duration = System.currentTimeMillis() - start;
        int totalMessages = guilds * messagesPerGuild;
        
        System.out.println("✅ Published " + totalMessages + " messages in " + duration + "ms");
        System.out.println("📊 Throughput: " + (totalMessages * 1000 / duration) + " msgs/sec");
        
        // Let messages drain
        System.out.println("⏳ Waiting for delivery...");
        Thread.sleep(5000);
        
        connection.close();
        redisClient.shutdown();
        executor.shutdown();
        
        System.out.println("🎉 Load test complete");
    }
    
    private static void connectWebSocket(String guildId, String clientId, CountDownLatch latch) throws Exception {
        Socket socket = new Socket(WS_HOST, WS_PORT);
        
        // Send upgrade request
        PrintWriter out = new PrintWriter(socket.getOutputStream());
        out.print("GET /ws?guild=" + guildId + " HTTP/1.1\r\n");
        out.print("Host: " + WS_HOST + "\r\n");
        out.print("Upgrade: websocket\r\n");
        out.print("Connection: Upgrade\r\n");
        out.print("Sec-WebSocket-Key: " + Base64.getEncoder().encodeToString("test".getBytes()) + "\r\n");
        out.print("Sec-WebSocket-Version: 13\r\n");
        out.print("\r\n");
        out.flush();
        
        // Read response
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.contains("101 Switching Protocols")) {
                System.out.println("[" + clientId + "] Connected to " + guildId);
                latch.countDown();
            }
        }
        
        // Keep reading frames
        int messagesReceived = 0;
        byte[] buffer = new byte[4096];
        InputStream rawIn = socket.getInputStream();
        
        while (rawIn.read(buffer) != -1) {
            messagesReceived++;
        }
        
        System.out.println("[" + clientId + "] Disconnected. Received: " + messagesReceived);
    }
}
