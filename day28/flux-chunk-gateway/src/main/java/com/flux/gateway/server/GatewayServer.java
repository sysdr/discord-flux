package com.flux.gateway.server;

import com.flux.gateway.assembler.ChunkAssembler;
import com.flux.gateway.connection.Connection;
import com.flux.gateway.connection.ConnectionRegistry;
import com.flux.gateway.dispatcher.ChunkDispatcher;
import com.flux.gateway.protocol.ChunkRequest;
import com.flux.gateway.protocol.ChunkResponse;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main Gateway Server - Handles WebSocket connections and chunk requests.
 */
public class GatewayServer {
    private static final int PORT = 9000;
    private static final String REDIS_URI = "redis://localhost:6379";
    
    private final ChunkDispatcher dispatcher = new ChunkDispatcher();
    private final ChunkAssembler assembler = new ChunkAssembler(REDIS_URI);
    private final ConnectionRegistry registry = new ConnectionRegistry();
    private final ScheduledExecutorService metricsExecutor = Executors.newScheduledThreadPool(1);
    
    private volatile boolean running = true;
    
    public void start() throws Exception {
        System.out.println("🚀 Flux Gateway starting on port " + PORT);
        System.out.println("📊 Dashboard: http://localhost:8080");
        System.out.println("🔌 WebSocket: ws://localhost:" + PORT);
        
        // Start worker threads (Virtual Threads)
        startWorkers(10);
        
        // Start metrics logger
        startMetrics();
        
        // Accept connections
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(PORT));
            serverChannel.configureBlocking(true);
            
            System.out.println("✅ Gateway ready - awaiting connections...\n");
            
            while (running) {
                SocketChannel clientChannel = serverChannel.accept();
                String connId = UUID.randomUUID().toString();
                
                Connection conn = new Connection(connId, clientChannel);
                registry.register(conn);
                
                // Handle client in Virtual Thread
                Thread.ofVirtual().name("client-" + connId).start(() -> 
                    handleClient(conn)
                );
            }
        }
    }
    
    private void startWorkers(int count) {
        for (int i = 0; i < count; i++) {
            Thread.ofVirtual().name("chunk-worker-" + i).start(() -> {
                while (running) {
                    ChunkRequest request = dispatcher.dequeue();
                    if (request == null) {
                        try {
                            Thread.sleep(10); // Brief pause if queue empty
                        } catch (InterruptedException e) {
                            break;
                        }
                        continue;
                    }
                    
                    processChunkRequest(request);
                }
            });
        }
    }
    
    private void processChunkRequest(ChunkRequest request) {
        Connection conn = registry.get(request.connectionId());
        if (conn == null) {
            return; // Connection closed
        }
        
        try {
            // Assemble chunks using cursor-based pagination
            List<ChunkResponse> chunks = assembler.assembleChunks(request);
            
            // Stream chunks to client
            for (ChunkResponse chunk : chunks) {
                conn.send(chunk.toJson() + "\n");
            }
            
            conn.decrementChunks();
            
        } catch (Exception e) {
            System.err.println("❌ Chunk processing failed: " + e.getMessage());
            try {
                conn.send("{\"op\":9,\"d\":{\"error\":\"" + e.getMessage() + "\"}}\n");
                conn.decrementChunks();
            } catch (Exception ignored) {}
        }
    }
    
    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final Pattern WS_KEY_PATTERN = Pattern.compile("Sec-WebSocket-Key:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);

    private void handleClient(Connection conn) {
        System.out.println("📥 New connection: " + conn.getId());
        
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        ByteBuffer wsBuffer = ByteBuffer.allocate(65536);
        wsBuffer.flip(); // empty
        StringBuilder handshakeAccum = new StringBuilder();
        
        try {
            while (running) {
                buffer.clear();
                int bytesRead = conn.getChannel().read(buffer);
                
                if (bytesRead == -1) {
                    break;
                }
                if (bytesRead == 0) {
                    continue;
                }
                
                buffer.flip();
                
                if (!conn.isWebSocketMode()) {
                    // Accumulate until we have full HTTP headers
                    handshakeAccum.append(StandardCharsets.UTF_8.decode(buffer));
                    String data = handshakeAccum.toString();
                    int headerEnd = data.indexOf("\r\n\r\n");
                    if (headerEnd < 0) headerEnd = data.indexOf("\n\n");
                    if (headerEnd < 0 && data.length() > 65536) {
                        break; // sanity: avoid unbounded growth
                    }
                    if (headerEnd < 0) {
                        continue; // need more data
                    }
                    String headers = data.substring(0, headerEnd);
                    if (headers.startsWith("GET ") && headers.toLowerCase().contains("upgrade") && headers.toLowerCase().contains("websocket")) {
                        String acceptKey = performWebSocketHandshake(conn, headers);
                        if (acceptKey != null) {
                            conn.setWebSocketMode(true);
                            handshakeAccum.setLength(0);
                            System.out.println("✅ WebSocket handshake OK: " + conn.getId());
                            continue;
                        }
                    }
                    // Raw TCP: handle as before
                    for (String message : data.split("\\R")) {
                        message = message.trim();
                        if (message.isEmpty()) continue;
                        if (message.contains("\"op\":8")) {
                            handleChunkRequest(conn, message);
                        }
                    }
                    handshakeAccum.setLength(0);
                    continue;
                }
                
                // WebSocket mode: accumulate and parse frames (always compact so we only have unprocessed bytes)
                wsBuffer.compact();
                wsBuffer.put(buffer);
                wsBuffer.flip();
                
                while (wsBuffer.remaining() >= 2) {
                    int pos = wsBuffer.position();
                    int b0 = wsBuffer.get(pos) & 0xFF;
                    int b1 = wsBuffer.get(pos + 1) & 0xFF;
                    int opcode = b0 & 0x0F;
                    boolean masked = (b1 & 0x80) != 0;
                    int payloadLen = b1 & 0x7F;
                    int headerLen = 2;
                    if (payloadLen == 126) {
                        if (wsBuffer.remaining() < 4) break;
                        headerLen = 4;
                        payloadLen = ((wsBuffer.get(pos + 2) & 0xFF) << 8) | (wsBuffer.get(pos + 3) & 0xFF);
                    } else if (payloadLen == 127) {
                        if (wsBuffer.remaining() < 10) break;
                        headerLen = 10;
                        wsBuffer.position(pos + 2);
                        payloadLen = (int) wsBuffer.getLong();
                        wsBuffer.position(pos);
                        if (payloadLen < 0 || payloadLen > 65536) break; // sanity
                    }
                    int maskLen = masked ? 4 : 0;
                    int frameLen = headerLen + maskLen + payloadLen;
                    if (frameLen > wsBuffer.remaining()) break;
                    
                    wsBuffer.position(pos + headerLen);
                    byte[] mask = masked ? new byte[4] : null;
                    if (masked) {
                        wsBuffer.get(mask);
                    }
                    byte[] payload = new byte[payloadLen];
                    wsBuffer.get(payload);
                    if (masked && mask != null) {
                        for (int i = 0; i < payload.length; i++) {
                            payload[i] ^= mask[i % 4];
                        }
                    }
                    wsBuffer.position(pos + frameLen);
                    
                    if (opcode == 9) {
                        // Ping: respond with pong to keep connection alive
                        try {
                            conn.sendPong(payload);
                        } catch (Exception ignored) {}
                    } else if (opcode == 1) {
                        String message = new String(payload, StandardCharsets.UTF_8);
                        if (message.contains("\"op\":8")) {
                            handleChunkRequest(conn, message);
                        }
                    }
                    // Ignore pong (10), close (8) - frame consumed
                }
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            System.err.println("⚠️  Client error: " + (msg != null ? msg : e.getClass().getSimpleName()));
            e.printStackTrace();
        } finally {
            registry.unregister(conn.getId());
            try {
                conn.getChannel().close();
            } catch (Exception ignored) {}
            System.out.println("📤 Connection closed: " + conn.getId());
        }
    }
    
    private String performWebSocketHandshake(Connection conn, String request) {
        try {
            Matcher m = WS_KEY_PATTERN.matcher(request);
            if (!m.find()) {
                System.err.println("❌ WebSocket handshake: Sec-WebSocket-Key not found");
                return null;
            }
            String key = m.group(1).trim().replaceAll("\\s+", "").replace("\r", "").replace("\n", "");
            if (key.isEmpty()) return null;
            byte[] hash = MessageDigest.getInstance("SHA-1")
                .digest((key + WS_MAGIC).getBytes(StandardCharsets.UTF_8));
            String accept = Base64.getEncoder().encodeToString(hash);
            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n"
                + "\r\n";
            conn.send(response);
            return accept;
        } catch (Exception e) {
            System.err.println("❌ WebSocket handshake failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private void handleChunkRequest(Connection conn, String message) {
        try {
            // Check backpressure
            if (!conn.canRequestChunk()) {
                conn.send("{\"op\":9,\"d\":{\"error\":\"Too many in-flight chunks\"}}\n");
                return;
            }
            
            ChunkRequest request = ChunkRequest.fromJson(conn.getId(), message);
            conn.incrementChunks();
            
            // Enqueue to ring buffer
            boolean enqueued = dispatcher.enqueue(request);
            if (!enqueued) {
                conn.send("{\"op\":9,\"d\":{\"error\":\"Server overloaded\"}}\n");
                conn.decrementChunks();
            } else {
                conn.send("{\"op\":9,\"d\":{\"status\":\"processing\",\"nonce\":\"" + 
                    request.nonce() + "\"}}\n");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Request parsing failed: " + e.getMessage());
        }
    }
    
    private static final String METRICS_FILE = "/tmp/flux-gateway-metrics.json";

    private void startMetrics() {
        // Write initial metrics immediately so dashboard /metrics can read the file
        writeMetrics();
        metricsExecutor.scheduleAtFixedRate(this::writeMetrics, 2, 2, TimeUnit.SECONDS);
    }

    private void writeMetrics() {
        int connections = registry.getActiveCount();
        double queuePct = dispatcher.getUtilization() * 100;
        int chunks = assembler.getChunksProcessed();
        long rejects = dispatcher.getRejectCount();
        System.out.printf(
            "📊 Connections: %d | Queue: %.1f%% | Chunks: %d | Rejects: %d%n",
            connections, queuePct, chunks, rejects
        );
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(METRICS_FILE),
                String.format("{\"connections\":%d,\"chunks\":%d,\"queue\":%.1f,\"rejects\":%d}",
                    connections, chunks, queuePct, rejects));
        } catch (Exception e) { /* ignore */ }
    }
    
    public void stop() {
        running = false;
        metricsExecutor.shutdown();
        assembler.close();
    }
    
    public static void main(String[] args) throws Exception {
        GatewayServer server = new GatewayServer();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down gateway...");
            server.stop();
        }));
        
        server.start();
    }
}
