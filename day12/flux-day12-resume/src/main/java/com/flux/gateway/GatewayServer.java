package com.flux.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
import java.util.Base64;

public class GatewayServer {
    private static final int PORT = Integer.parseInt(
        System.getProperty("gateway.port", System.getenv().getOrDefault("GATEWAY_PORT", "8080"))
    );
    private static final int RING_BUFFER_SIZE = 512;
    private static final long SESSION_TTL_MS = 5 * 60 * 1000; // 5 minutes
    
    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SocketChannel, String> channelToSession = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Metrics metrics = new Metrics();
    
    // Sample session counters for demonstration (without real connections)
    private volatile int sampleActiveSessions = 0;
    private volatile int sampleDisconnectedSessions = 0;
    
    private volatile boolean running = true;
    
    public static void main(String[] args) throws Exception {
        GatewayServer server = new GatewayServer();
        server.start();
    }
    
    public void start() throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(PORT));
        serverChannel.configureBlocking(false);
        
        Selector selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("Gateway Server started on port " + PORT);
        System.out.println("WebSocket endpoint: ws://localhost:" + PORT);
        
        // Start session cleanup task
        scheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 30, 30, TimeUnit.SECONDS);
        
        // Start metrics reporting
        scheduler.scheduleAtFixedRate(metrics::report, 10, 10, TimeUnit.SECONDS);
        
        // Start Dashboard Server
        DashboardServer dashboard = new DashboardServer(this);
        Thread.ofVirtual().start(() -> {
            try {
                dashboard.start();
            } catch (IOException e) {
                System.err.println("Failed to start dashboard: " + e.getMessage());
            }
        });
        
        // Event loop
        while (running) {
            selector.select(1000);
            
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                
                if (!key.isValid()) continue;
                
                if (key.isAcceptable()) {
                    handleAccept(serverChannel, selector);
                } else if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }
    
    private void handleAccept(ServerSocketChannel serverChannel, Selector selector) throws IOException {
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        
        System.out.println("New connection from: " + channel.getRemoteAddress());
    }
    
    private void handleRead(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        
        try {
            int bytesRead = channel.read(buffer);
            if (bytesRead == -1) {
                handleDisconnect(channel);
                return;
            }
            
            buffer.flip();
            
            // Check if this is a WebSocket upgrade request (HTTP)
            if (buffer.remaining() > 0 && (buffer.get(0) & 0xF0) != 0x80) {
                String data = StandardCharsets.UTF_8.decode(buffer.duplicate()).toString();
                if (data.contains("Upgrade: websocket")) {
                    handleWebSocketUpgrade(channel, data);
                    return;
                }
            }
            
            // Parse WebSocket frame (binary)
            if (buffer.remaining() >= 2) {
                processWebSocketFrame(channel, buffer);
            }
            
        } catch (IOException e) {
            handleDisconnect(channel);
        }
    }
    
    private void handleWebSocketUpgrade(SocketChannel channel, String request) throws IOException {
        String[] lines = request.split("\r\n");
        String key = null;
        
        for (String line : lines) {
            if (line.startsWith("Sec-WebSocket-Key:")) {
                key = line.substring(19).trim();
                break;
            }
        }
        
        if (key == null) {
            channel.close();
            return;
        }
        
        String acceptKey = generateAcceptKey(key);
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                         "Upgrade: websocket\r\n" +
                         "Connection: Upgrade\r\n" +
                         "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
        
        channel.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
        
        // Send HELLO message
        String sessionId = UUID.randomUUID().toString();
        sendMessage(channel, new Message(OpCode.HELLO, 0, 
            "{\"session_id\":\"" + sessionId + "\",\"heartbeat_interval\":45000}"));
        
        System.out.println("WebSocket upgraded, session: " + sessionId);
    }
    
    private String generateAcceptKey(String key) {
        try {
            String magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest((key + magic).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private void processWebSocketFrame(SocketChannel channel, ByteBuffer buffer) {
        try {
            String payload = extractPayload(buffer);
            if (payload == null || payload.isEmpty()) return;
            
            // Debug: log received payload
            System.out.println("WS payload: " + payload);
            
            Integer opCode = extractOpCode(payload);
            if (opCode == null) {
                System.err.println("Missing or invalid 'op' in payload: " + payload);
                return;
            }
            
            switch (opCode) {
                case 2 -> handleIdentifyRaw(channel, payload);
                case 6 -> handleResumeRaw(channel, payload);
                case 1 -> handleHeartbeat(channel);
                default -> System.out.println("Unknown opcode: " + opCode);
            }
        } catch (Exception e) {
            System.err.println("Error processing frame: " + e.getMessage());
        }
    }
    
    // Simple opcode extractor that avoids brittle JSON parsing
    private Integer extractOpCode(String payload) {
        try {
            int idx = payload.indexOf("\"op\":");
            if (idx < 0) return null;
            int start = idx + 5;
            int end = payload.indexOf(",", start);
            if (end < 0) end = payload.indexOf("}", start);
            if (end < 0) return null;
            return Integer.parseInt(payload.substring(start, end).trim());
        } catch (Exception e) {
            return null;
        }
    }
    
    private void handleIdentifyRaw(SocketChannel channel, String payload) {
        String sessionId = extractStringField(payload, "session_id");
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        
        SessionState session = new SessionState(sessionId, channel, RING_BUFFER_SIZE);
        sessions.put(sessionId, session);
        channelToSession.put(channel, sessionId);
        metrics.incrementIdentify();
        System.out.println("Client identified: " + sessionId);
        
        sendMessage(channel, new Message(OpCode.DISPATCH, session.nextSequence(),
            "{\"type\":\"READY\",\"user_id\":\"user_" + sessionId.substring(0, 8) + "\"}"));
    }
    
    private void handleResumeRaw(SocketChannel channel, String payload) {
        long startTime = System.nanoTime();
        String sessionId = extractStringField(payload, "session_id");
        long clientSeq = extractLongField(payload, "seq", 0L);

        try {
            if (sessionId == null || sessionId.isBlank()) {
                logResumeFailure(null, clientSeq, "missing session_id in payload: " + payload);
                sendMessage(channel, new Message(OpCode.DISPATCH, 0,
                    "{\"type\":\"INVALID_SESSION\",\"resumable\":false}"));
                metrics.incrementResumeFailed();
                return;
            }

            SessionState session = sessions.get(sessionId);
            if (session == null) {
                logResumeFailure(sessionId, clientSeq, "no session found");
                sendMessage(channel, new Message(OpCode.DISPATCH, 0,
                    "{\"type\":\"INVALID_SESSION\",\"resumable\":false}"));
                metrics.incrementResumeFailed();
                return;
            }

            if (session.getState() == SessionState.State.EXPIRED) {
                logResumeFailure(sessionId, clientSeq, "session expired");
                sendMessage(channel, new Message(OpCode.DISPATCH, 0,
                    "{\"type\":\"INVALID_SESSION\",\"resumable\":false}"));
                metrics.incrementResumeFailed();
                return;
            }

            if (!session.resume(channel)) {
                logResumeFailure(sessionId, clientSeq,
                    "resume rejected because state=" + session.getState());
                sendMessage(channel, new Message(OpCode.DISPATCH, 0,
                    "{\"type\":\"INVALID_SESSION\",\"resumable\":false}"));
                metrics.incrementResumeFailed();
                return;
            }

            channelToSession.put(channel, sessionId);

            Message[] missedMessages = session.getMessagesSince(clientSeq + 1);
            long currentSeq = session.currentSequence();
            sendMessage(channel, new Message(OpCode.RESUMED, currentSeq,
                "{\"seq\":" + currentSeq + ",\"replayed\":" + missedMessages.length + "}"));
            for (Message missedMsg : missedMessages) {
                sendMessage(channel, missedMsg);
            }

            long latencyNanos = System.nanoTime() - startTime;
            metrics.recordResumeLatency(latencyNanos / 1_000_000.0); // Convert to ms
            metrics.incrementResumeSuccess();
            System.out.println("Session resumed: " + sessionId + " (replayed " + missedMessages.length + " messages in " +
                             String.format("%.2f", latencyNanos / 1_000_000.0) + "ms)");
        } catch (Exception e) {
            logResumeFailure(sessionId, clientSeq, "exception: " + e + " payload=" + payload);
            sendMessage(channel, new Message(OpCode.DISPATCH, 0,
                "{\"type\":\"INVALID_SESSION\",\"resumable\":false}"));
            metrics.incrementResumeFailed();
        }
    }

    private void logResumeFailure(String sessionId, long clientSeq, String reason) {
        System.err.println("[RESUME][FAIL] sessionId=" + sessionId +
            " clientSeq=" + clientSeq + " reason=" + reason +
            " activeSessions=" + sessions.size() +
            " channelMapSize=" + channelToSession.size());
    }
    
    private String extractStringField(String payload, String field) {
        try {
            String key = "\"" + field + "\":\"";
            int start = payload.indexOf(key);
            if (start < 0) return null;
            start += key.length();
            int end = payload.indexOf("\"", start);
            if (end < 0) return null;
            return payload.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
    
    private long extractLongField(String payload, String field, long defaultValue) {
        try {
            String key = "\"" + field + "\":";
            int start = payload.indexOf(key);
            if (start < 0) return defaultValue;
            start += key.length();
            int end = payload.indexOf(",", start);
            if (end < 0) end = payload.indexOf("}", start);
            if (end < 0) return defaultValue;
            return Long.parseLong(payload.substring(start, end).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private void handleIdentify(SocketChannel channel, Map<String, Object> msg) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) msg.get("d");
        String sessionId = (String) data.get("session_id");
        
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        
        // Create new session
        SessionState session = new SessionState(sessionId, channel, RING_BUFFER_SIZE);
        sessions.put(sessionId, session);
        channelToSession.put(channel, sessionId);
        
        metrics.incrementIdentify();
        
        System.out.println("Client identified: " + sessionId);
        
        // Send ready message
        sendMessage(channel, new Message(OpCode.DISPATCH, session.nextSequence(),
            "{\"type\":\"READY\",\"user_id\":\"user_" + sessionId.substring(0, 8) + "\"}"));
    }
    
    private void handleResume(SocketChannel channel, Map<String, Object> msg) {
        long startTime = System.nanoTime();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) msg.get("d");
        String sessionId = (String) data.get("session_id");
        long clientSeq = ((Number) data.get("seq")).longValue();
        
        SessionState session = sessions.get(sessionId);
        
        if (session == null || session.getState() == SessionState.State.EXPIRED) {
            // Session not found or expired, force re-identify
            sendMessage(channel, new Message(OpCode.DISPATCH, 0,
                "{\"type\":\"INVALID_SESSION\",\"resumable\":false}"));
            metrics.incrementResumeFailed();
            return;
        }
        
        // Attempt to resume
        if (!session.resume(channel)) {
            sendMessage(channel, new Message(OpCode.DISPATCH, 0,
                "{\"type\":\"INVALID_SESSION\",\"resumable\":false}"));
            metrics.incrementResumeFailed();
            return;
        }
        
        channelToSession.put(channel, sessionId);
        
        // Replay missed messages
        Message[] missedMessages = session.getMessagesSince(clientSeq + 1);
        
        // Send RESUMED message
        long currentSeq = session.currentSequence();
        sendMessage(channel, new Message(OpCode.RESUMED, currentSeq,
            "{\"seq\":" + currentSeq + ",\"replayed\":" + missedMessages.length + "}"));
        
        // Replay messages
        for (Message missedMsg : missedMessages) {
            sendMessage(channel, missedMsg);
        }
        
        long latencyNanos = System.nanoTime() - startTime;
        metrics.recordResumeLatency(latencyNanos / 1_000_000.0); // Convert to ms
        metrics.incrementResumeSuccess();
        
        System.out.println("Session resumed: " + sessionId + 
                         " (replayed " + missedMessages.length + " messages in " +
                         String.format("%.2f", latencyNanos / 1_000_000.0) + "ms)");
    }
    
    private void handleHeartbeat(SocketChannel channel) {
        String sessionId = channelToSession.get(channel);
        if (sessionId != null) {
            SessionState session = sessions.get(sessionId);
            if (session != null) {
                sendMessage(channel, new Message(OpCode.HEARTBEAT_ACK, 
                    session.currentSequence(), "{}"));
            }
        }
    }
    
    private void handleDisconnect(SocketChannel channel) {
        String sessionId = channelToSession.remove(channel);
        
        if (sessionId != null) {
            SessionState session = sessions.get(sessionId);
            if (session != null && session.disconnect()) {
                // Schedule cleanup after TTL
                scheduler.schedule(() -> {
                    if (session.getState() == SessionState.State.DISCONNECTED) {
                        session.expire();
                        sessions.remove(sessionId);
                        session.cleanup();
                        System.out.println("Session expired: " + sessionId);
                    }
                }, SESSION_TTL_MS, TimeUnit.MILLISECONDS);
                
                System.out.println("Session disconnected (resumable for " + 
                                 (SESSION_TTL_MS / 1000) + "s): " + sessionId);
            }
        }
        
        try {
            channel.close();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int cleaned = 0;
        
        for (SessionState session : sessions.values()) {
            if (session.getState() == SessionState.State.DISCONNECTED) {
                long disconnectDuration = now - session.getDisconnectTime();
                if (disconnectDuration > SESSION_TTL_MS) {
                    if (session.expire()) {
                        sessions.remove(session.getSessionId());
                        session.cleanup();
                        cleaned++;
                    }
                }
            }
        }
        
        if (cleaned > 0) {
            System.out.println("Cleaned up " + cleaned + " expired sessions");
        }
    }
    
    private void sendMessage(SocketChannel channel, Message message) {
        if (channel == null || !channel.isConnected()) return;
        
        try {
            String json = message.toJson();
            ByteBuffer buffer = encodeWebSocketFrame(json);
            channel.write(buffer);
            
            // Store in session's ring buffer
            String sessionId = channelToSession.get(channel);
            if (sessionId != null) {
                SessionState session = sessions.get(sessionId);
                if (session != null && message.op() == OpCode.DISPATCH) {
                    session.storeMessage(message);
                }
            }
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }
    
    // Simplified WebSocket frame encoding
    private ByteBuffer encodeWebSocketFrame(String data) {
        byte[] payload = data.getBytes(StandardCharsets.UTF_8);
        int frameSize = 2 + (payload.length < 126 ? 0 : 2) + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(frameSize);
        
        buffer.put((byte) 0x81); // Text frame, FIN bit set
        
        if (payload.length < 126) {
            buffer.put((byte) payload.length);
        } else {
            buffer.put((byte) 126);
            buffer.putShort((short) payload.length);
        }
        
        buffer.put(payload);
        buffer.flip();
        return buffer;
    }
    
    // Simplified WebSocket frame decoding
    private String extractPayload(ByteBuffer buffer) {
        if (buffer.remaining() < 2) return null;
        
        byte firstByte = buffer.get(0);
        byte secondByte = buffer.get(1);
        
        int opcode = firstByte & 0x0F;
        if (opcode != 1) { // only handle text frames
            return null;
        }
        
        boolean masked = (secondByte & 0x80) != 0;
        int payloadLen = secondByte & 0x7F;
        int offset = 2;
        
        if (payloadLen == 126) {
            if (buffer.limit() < 4) return null;
            payloadLen = ((buffer.get(2) & 0xFF) << 8) | (buffer.get(3) & 0xFF);
            offset = 4;
        } else if (payloadLen == 127) {
            // Not expected for our small messages
            return null;
        }
        
        byte[] maskKey = null;
        if (masked) {
            if (buffer.limit() < offset + 4) return null;
            maskKey = new byte[4];
            buffer.position(offset);
            buffer.get(maskKey);
            offset += 4;
        }
        
        if (buffer.limit() < offset + payloadLen) return null;
        
        byte[] payload = new byte[payloadLen];
        buffer.position(offset);
        buffer.get(payload);
        
        if (masked && maskKey != null) {
            for (int i = 0; i < payloadLen; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }
        
        return new String(payload, StandardCharsets.UTF_8);
    }
    
    // Simple JSON parser (production would use proper parser)
    private Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> result = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("\"", "");
                    String value = kv[1].trim();
                    
                    if (value.startsWith("{") || value.startsWith("[")) {
                        result.put(key, parseSimpleJson(value));
                    } else if (value.startsWith("\"")) {
                        result.put(key, value.replaceAll("\"", ""));
                    } else {
                        try {
                            if (value.contains(".")) {
                                result.put(key, Double.parseDouble(value));
                            } else {
                                result.put(key, Long.parseLong(value));
                            }
                        } catch (NumberFormatException e) {
                            result.put(key, value);
                        }
                    }
                }
            }
        }
        return result;
    }
    
    public Metrics getMetrics() {
        return metrics;
    }
    
    public int getActiveSessionCount() {
        int realActive = (int) sessions.values().stream()
            .filter(s -> s.getState() == SessionState.State.ACTIVE)
            .count();
        return realActive + sampleActiveSessions;
    }
    
    public int getDisconnectedSessionCount() {
        int realDisconnected = (int) sessions.values().stream()
            .filter(s -> s.getState() == SessionState.State.DISCONNECTED)
            .count();
        return realDisconnected + sampleDisconnectedSessions;
    }
    
    // Generate sample metrics and sessions for demonstration
    public void generateSampleMetrics() {
        // Generate sample metrics data
        metrics.generateSampleData();
        
        // Set sample session counts for demonstration
        sampleActiveSessions = 42;
        sampleDisconnectedSessions = 15;
        
        System.out.println("Generated sample metrics data for demonstration");
        System.out.println("Sample active sessions: " + sampleActiveSessions);
        System.out.println("Sample disconnected sessions: " + sampleDisconnectedSessions);
    }
}
