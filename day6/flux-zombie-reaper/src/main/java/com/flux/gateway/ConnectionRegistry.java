package com.flux.gateway;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConnectionRegistry {
    private final ConcurrentHashMap<String, Connection> connections;
    private final AtomicInteger activeCount;
    
    public ConnectionRegistry() {
        this.connections = new ConcurrentHashMap<>();
        this.activeCount = new AtomicInteger(0);
    }
    
    public void register(Connection connection) {
        connections.put(connection.id(), connection);
        activeCount.incrementAndGet();
    }
    
    public Optional<Connection> get(String connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }
    
    public void remove(String connectionId) {
        Connection removed = connections.remove(connectionId);
        if (removed != null) {
            activeCount.decrementAndGet();
        }
    }
    
    public int getActiveCount() {
        return activeCount.get();
    }
    
    public void clear() {
        connections.clear();
        activeCount.set(0);
    }
}
