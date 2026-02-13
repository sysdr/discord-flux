package com.flux.gateway;

import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionManager {
    private final Map<String, SocketChannel> connections = new ConcurrentHashMap<>();
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    
    public void addConnection(String connectionId, SocketChannel channel) {
        connections.put(connectionId, channel);
        activeConnections.incrementAndGet();
        System.out.println("[ConnectionManager] Added connection: " + connectionId + 
                         " (Total: " + activeConnections.get() + ")");
    }
    
    public void removeConnection(String connectionId) {
        var channel = connections.remove(connectionId);
        if (channel != null) {
            activeConnections.decrementAndGet();
            System.out.println("[ConnectionManager] Removed connection: " + connectionId + 
                             " (Total: " + activeConnections.get() + ")");
        }
    }
    
    public int getActiveConnectionCount() {
        return activeConnections.get();
    }
    
    public void closeAllConnections() {
        System.out.println("[ConnectionManager] Closing all connections (" + connections.size() + ")");
        connections.forEach((id, channel) -> {
            try {
                channel.close();
            } catch (Exception e) {
                System.err.println("[ConnectionManager] Error closing connection " + id + ": " + e.getMessage());
            }
        });
        connections.clear();
        activeConnections.set(0);
    }
}
