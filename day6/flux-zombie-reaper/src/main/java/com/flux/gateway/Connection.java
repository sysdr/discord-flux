package com.flux.gateway;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class Connection {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);
    
    private final String id;
    private final SocketChannel channel;
    private final AtomicReference<Instant> lastHeartbeat;
    private final Instant createdAt;
    
    public Connection(SocketChannel channel) {
        this.id = "conn-" + ID_GENERATOR.incrementAndGet();
        this.channel = channel;
        this.lastHeartbeat = new AtomicReference<>(Instant.now());
        this.createdAt = Instant.now();
    }
    
    public String id() {
        return id;
    }
    
    public SocketChannel channel() {
        return channel;
    }
    
    public void updateLastHeartbeat() {
        lastHeartbeat.set(Instant.now());
    }
    
    public Instant getLastHeartbeat() {
        return lastHeartbeat.get();
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            System.err.println("Error closing connection " + id + ": " + e.getMessage());
        }
    }
    
    public boolean isOpen() {
        return channel.isOpen();
    }
    
    @Override
    public String toString() {
        return "Connection{id='" + id + "', lastHeartbeat=" + lastHeartbeat.get() + "}";
    }
}
