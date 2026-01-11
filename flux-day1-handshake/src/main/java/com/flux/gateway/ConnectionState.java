package com.flux.gateway;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ConnectionState {
    
    public enum Phase {
        AWAITING_HEADERS,
        COMPUTING_KEY,
        READY_FOR_UPGRADE,
        WEBSOCKET_ACTIVE,
        CLOSED
    }
    
    private final SocketChannel channel;
    private final ByteBuffer readBuffer;
    private final long connectedAt;
    private volatile Phase phase;
    private volatile String acceptKey;
    
    public ConnectionState(SocketChannel channel) {
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocateDirect(8192); // 8KB direct buffer
        this.connectedAt = System.currentTimeMillis();
        this.phase = Phase.AWAITING_HEADERS;
    }
    
    public SocketChannel channel() { return channel; }
    public ByteBuffer readBuffer() { return readBuffer; }
    public Phase phase() { return phase; }
    public void phase(Phase phase) { this.phase = phase; }
    public String acceptKey() { return acceptKey; }
    public void acceptKey(String key) { this.acceptKey = key; }
    public long connectedAt() { return connectedAt; }
    
    public boolean isStale(long maxAgeMs) {
        return System.currentTimeMillis() - connectedAt > maxAgeMs;
    }
}
