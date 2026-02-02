package com.flux.gateway.connection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe registry of active connections.
 */
public class ConnectionRegistry {
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    
    public void register(Connection connection) {
        connections.put(connection.getId(), connection);
        totalConnections.incrementAndGet();
    }
    
    public void unregister(String connectionId) {
        connections.remove(connectionId);
    }
    
    public Connection get(String connectionId) {
        return connections.get(connectionId);
    }
    
    public int getActiveCount() {
        return connections.size();
    }
    
    public int getTotalConnectionsServed() {
        return totalConnections.get();
    }
}
