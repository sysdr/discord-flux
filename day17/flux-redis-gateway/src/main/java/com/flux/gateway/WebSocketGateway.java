package com.flux.gateway;

import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * WebSocket Gateway with Redis Stream integration.
 * Handles WebSocket protocol, Ring Buffer draining, and metrics.
 */
public class WebSocketGateway {
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int PORT = 9090;
    private static final String REDIS_URL = "redis://localhost:6379";
    
    private final ExecutorService virtualThreadPool = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, ConnectionContext> connections = new ConcurrentHashMap<>();
    private final Map<String, RedisStreamConsumer> guildConsumers = new ConcurrentHashMap<>();
    private final GuildRouter router = new GuildRouter(3, 0); // 3 instances, this is #0
    private final Gson gson = new Gson();
    
    private final AtomicInteger connectionCounter = new AtomicInteger(0);
    private volatile boolean running = true;
    
    record ConnectionContext(
        String id,
        Socket socket,
        String guildId,
        RingBuffer buffer,
        AtomicInteger messagesSent
    ) {}
    
    public void start() throws IOException {
        ServerSocket server = new ServerSocket(PORT);
        System.out.println("WebSocket Gateway started on port " + PORT);
        System.out.println("📡 Redis URL: " + REDIS_URL);
        
        while (running) {
            try {
                Socket client = server.accept();
                virtualThreadPool.submit(() -> handleConnection(client));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Accept error: " + e.getMessage());
                }
            }
        }
    }
    
    private void handleConnection(Socket socket) {
        try {
            // Read HTTP upgrade request
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            );
            
            String requestLine = reader.readLine();
            if (requestLine == null || !requestLine.startsWith("GET")) {
                socket.close();
                return;
            }
            
            // Extract path from request line before parsing headers
            String path = requestLine.split(" ")[1];
            
            // Parse headers
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String[] parts = line.split(": ", 2);
                if (parts.length == 2) {
                    headers.put(parts[0].toLowerCase(), parts[1]);
                }
            }
            
            // Extract guild ID from path: /ws?guild=123
            String guildId = extractGuildId(path);
            
            if (guildId == null || !router.shouldHandle(guildId)) {
                socket.close();
                return;
            }
            
            // Perform WebSocket handshake
            String key = headers.get("sec-websocket-key");
            String accept = generateAcceptKey(key);
            
            PrintWriter writer = new PrintWriter(socket.getOutputStream());
            writer.print("HTTP/1.1 101 Switching Protocols\r\n");
            writer.print("Upgrade: websocket\r\n");
            writer.print("Connection: Upgrade\r\n");
            writer.print("Sec-WebSocket-Accept: " + accept + "\r\n");
            writer.print("\r\n");
            writer.flush();
            
            // Create connection context
            String connId = "conn-" + connectionCounter.incrementAndGet();
            RingBuffer buffer = new RingBuffer(256);
            ConnectionContext ctx = new ConnectionContext(
                connId, socket, guildId, buffer, new AtomicInteger(0)
            );
            connections.put(connId, ctx);
            
            System.out.println("[" + connId + "] Connected to guild: " + guildId);
            
            // Start Redis consumer for this guild (if not already running)
            String streamKey = router.streamKey(guildId);
            guildConsumers.computeIfAbsent(streamKey, k -> {
                RedisStreamConsumer consumer = new RedisStreamConsumer(REDIS_URL, k, buffer);
                virtualThreadPool.submit(consumer);
                return consumer;
            });
            
            // Start draining Ring Buffer to WebSocket
            virtualThreadPool.submit(() -> drainToSocket(ctx));
            
            // Keep connection alive (read ping/pong frames)
            keepAlive(ctx);
            
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }
    
    private void drainToSocket(ConnectionContext ctx) {
        try {
            OutputStream out = ctx.socket.getOutputStream();
            
            while (!ctx.socket.isClosed()) {
                String message = ctx.buffer.poll();
                
                if (message != null) {
                    byte[] payload = message.getBytes(StandardCharsets.UTF_8);
                    byte[] frame = encodeFrame(payload);
                    out.write(frame);
                    out.flush();
                    ctx.messagesSent.incrementAndGet();
                } else {
                    // Buffer empty, park for 1ms
                    LockSupport.parkNanos(1_000_000);
                }
            }
        } catch (IOException e) {
            // Connection closed
        } finally {
            cleanup(ctx);
        }
    }
    
    private void keepAlive(ConnectionContext ctx) {
        try {
            InputStream in = ctx.socket.getInputStream();
            byte[] buffer = new byte[4096];
            
            while (in.read(buffer) != -1) {
                // Just consume ping/pong frames
                // Real implementation would parse and respond to pings
            }
        } catch (IOException e) {
            // Connection closed
        }
    }
    
    private void cleanup(ConnectionContext ctx) {
        connections.remove(ctx.id);
        try {
            ctx.socket.close();
        } catch (IOException e) {
            // Ignore
        }
        System.out.println("[" + ctx.id + "] Disconnected. Sent: " + ctx.messagesSent.get());
    }
    
    private String extractGuildId(String path) {
        try {
            URI uri = new URI(path);
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2 && kv[0].equals("guild")) {
                        return kv[1];
                    }
                }
            }
        } catch (Exception e) {
            // Invalid URI
        }
        return null;
    }
    
    private String generateAcceptKey(String key) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest((key + WS_GUID).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
    
    private byte[] encodeFrame(byte[] payload) {
        int len = payload.length;
        ByteBuffer buffer;
        
        if (len < 126) {
            buffer = ByteBuffer.allocate(2 + len);
            buffer.put((byte) 0x81); // FIN + text frame
            buffer.put((byte) len);
        } else if (len < 65536) {
            buffer = ByteBuffer.allocate(4 + len);
            buffer.put((byte) 0x81);
            buffer.put((byte) 126);
            buffer.putShort((short) len);
        } else {
            buffer = ByteBuffer.allocate(10 + len);
            buffer.put((byte) 0x81);
            buffer.put((byte) 127);
            buffer.putLong(len);
        }
        
        buffer.put(payload);
        return buffer.array();
    }
    
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("connections", connections.size());
        metrics.put("guilds", guildConsumers.size());
        
        List<Map<String, Object>> connDetails = new ArrayList<>();
        for (ConnectionContext ctx : connections.values()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", ctx.id);
            detail.put("guild", ctx.guildId);
            detail.put("bufferUtil", ctx.buffer.utilization());
            detail.put("messagesSent", ctx.messagesSent.get());
            connDetails.add(detail);
        }
        metrics.put("connectionDetails", connDetails);
        
        return metrics;
    }
    
    public void shutdown() {
        running = false;
        guildConsumers.values().forEach(RedisStreamConsumer::stop);
        virtualThreadPool.shutdown();
    }
}
