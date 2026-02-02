package com.flux.gateway.connection;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a WebSocket connection with backpressure tracking.
 */
public class Connection {
    private static final int MAX_INFLIGHT_CHUNKS = 5;
    
    private final String id;
    private final SocketChannel channel;
    private final AtomicInteger inflightChunks = new AtomicInteger(0);
    private volatile boolean webSocketMode = false;
    
    public Connection(String id, SocketChannel channel) {
        this.id = id;
        this.channel = channel;
    }
    
    public void setWebSocketMode(boolean webSocketMode) {
        this.webSocketMode = webSocketMode;
    }
    
    public boolean isWebSocketMode() {
        return webSocketMode;
    }
    
    public String getId() {
        return id;
    }
    
    public SocketChannel getChannel() {
        return channel;
    }
    
    /**
     * Check if connection can accept another chunk request.
     */
    public boolean canRequestChunk() {
        return inflightChunks.get() < MAX_INFLIGHT_CHUNKS;
    }
    
    /**
     * Increment in-flight chunk counter. Throws if limit exceeded.
     */
    public void incrementChunks() {
        int current = inflightChunks.incrementAndGet();
        if (current > MAX_INFLIGHT_CHUNKS) {
            inflightChunks.decrementAndGet();
            throw new IllegalStateException(
                "Connection " + id + " has too many in-flight chunks: " + current
            );
        }
    }
    
    /**
     * Decrement when chunk completes.
     */
    public void decrementChunks() {
        inflightChunks.decrementAndGet();
    }
    
    public int getInflightChunks() {
        return inflightChunks.get();
    }
    
    /**
     * Send message to client (blocking write).
     * In WebSocket mode, wraps payload in a WebSocket text frame.
     */
    public void send(String message) throws Exception {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer;
        if (webSocketMode) {
            // WebSocket text frame: FIN=1, opcode=1 (text), no mask, payload
            int len = payload.length;
            if (len <= 125) {
                buffer = ByteBuffer.allocate(2 + len);
                buffer.put((byte) 0x81);
                buffer.put((byte) len);
            } else if (len <= 65535) {
                buffer = ByteBuffer.allocate(4 + len);
                buffer.put((byte) 0x81);
                buffer.put((byte) 126);
                buffer.put((byte) (len >> 8));
                buffer.put((byte) (len & 0xFF));
            } else {
                buffer = ByteBuffer.allocate(10 + len);
                buffer.put((byte) 0x81);
                buffer.put((byte) 127);
                buffer.putLong(len);
            }
            buffer.put(payload);
            buffer.flip();
        } else {
            buffer = ByteBuffer.wrap(payload);
        }
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /**
     * Send WebSocket pong frame (opcode 10) in response to ping. Call only when webSocketMode is true.
     */
    public void sendPong(byte[] payload) throws Exception {
        if (!webSocketMode || payload == null) return;
        int len = payload.length;
        ByteBuffer buffer;
        if (len <= 125) {
            buffer = ByteBuffer.allocate(2 + len);
            buffer.put((byte) 0x8A); // FIN + pong
            buffer.put((byte) len);
        } else if (len <= 65535) {
            buffer = ByteBuffer.allocate(4 + len);
            buffer.put((byte) 0x8A);
            buffer.put((byte) 126);
            buffer.put((byte) (len >> 8));
            buffer.put((byte) (len & 0xFF));
        } else {
            buffer = ByteBuffer.allocate(10 + len);
            buffer.put((byte) 0x8A);
            buffer.put((byte) 127);
            buffer.putLong(len);
        }
        buffer.put(payload);
        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
}
