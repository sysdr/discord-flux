package com.flux.integrationtest.client;

import com.flux.integrationtest.gateway.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight WebSocket client simulator using blocking I/O on Virtual Threads.
 * Each client runs in its own Virtual Thread, allowing 1,000+ concurrent clients
 * with minimal memory overhead.
 */
public class WebSocketSimulator implements AutoCloseable {
    private final long clientId;
    private SocketChannel socket;
    private final Random random = new Random();
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    
    private volatile boolean connected = false;
    private volatile int readDelayMs = 0; // For slow consumer simulation
    
    public WebSocketSimulator(long clientId) {
        this.clientId = clientId;
    }
    
    public void connect() throws IOException {
        socket = SocketChannel.open();
        socket.configureBlocking(true); // Virtual Thread can block safely
        socket.connect(new InetSocketAddress("localhost", 8080));
        connected = true;
    }
    
    public void send(Message msg) throws IOException {
        if (!connected) throw new IOException("Not connected");
        
        String json = String.format(
            "{\"senderId\":%d,\"timestamp\":%d,\"content\":\"%s\",\"type\":\"%s\"}",
            msg.senderId(), msg.timestamp(), msg.content(), msg.type()
        );
        
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        
        while (buffer.hasRemaining()) {
            socket.write(buffer);
        }
        
        messagesSent.incrementAndGet();
    }
    
    public Message receive() throws IOException {
        if (!connected) throw new IOException("Not connected");
        
        // Simulate slow consumer
        if (readDelayMs > 0) {
            try {
                Thread.sleep(readDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        int bytesRead = socket.read(buffer);
        
        if (bytesRead == -1) {
            throw new IOException("Connection closed");
        }
        
        if (bytesRead > 0) {
            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            String json = new String(bytes, StandardCharsets.UTF_8);
            
            messagesReceived.incrementAndGet();
            
            // Parse JSON (simplified)
            long timestamp = parseTimestamp(json);
            return new Message(clientId, timestamp, json, Message.MessageType.CHAT);
        }
        
        return null;
    }
    
    private long parseTimestamp(String json) {
        try {
            int start = json.indexOf("\"timestamp\":") + 12;
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return Long.parseLong(json.substring(start, end));
        } catch (Exception e) {
            return System.nanoTime();
        }
    }
    
    public void setReadDelay(int delayMs) {
        this.readDelayMs = delayMs;
    }
    
    public long getClientId() { return clientId; }
    public long getMessagesSent() { return messagesSent.get(); }
    public long getMessagesReceived() { return messagesReceived.get(); }
    public boolean isConnected() { return connected; }
    
    @Override
    public void close() {
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}
