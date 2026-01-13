package com.flux.gateway.core;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Immutable connection metadata with mutable state holders.
 * Uses record for immutable fields, separate objects for mutable state.
 */
public record Connection(
    long id,
    SocketChannel channel,
    ByteBuffer readBuffer,
    ConcurrentLinkedQueue<ByteBuffer> writeQueue,
    ConnectionStateHolder stateHolder,
    long connectedAt
) {
    public Connection {
        if (readBuffer == null || writeQueue == null || stateHolder == null) {
            throw new IllegalArgumentException("Buffers and state cannot be null");
        }
    }
    
    public static Connection create(long id, SocketChannel channel) {
        var readBuffer = ByteBuffer.allocateDirect(8192);
        var writeQueue = new ConcurrentLinkedQueue<ByteBuffer>();
        var stateHolder = new ConnectionStateHolder(ConnectionState.HANDSHAKE);
        return new Connection(id, channel, readBuffer, writeQueue, stateHolder, System.currentTimeMillis());
    }
    
    public ConnectionState state() {
        return stateHolder.get();
    }
    
    public void transitionTo(ConnectionState newState) {
        stateHolder.set(newState);
    }
    
    public long uptime() {
        return System.currentTimeMillis() - connectedAt;
    }
    
    /**
     * Mutable holder for connection state (allows mutation within record)
     */
    public static class ConnectionStateHolder {
        private volatile ConnectionState state;
        
        public ConnectionStateHolder(ConnectionState initial) {
            this.state = initial;
        }
        
        public ConnectionState get() {
            return state;
        }
        
        public void set(ConnectionState newState) {
            this.state = newState;
        }
    }
}
