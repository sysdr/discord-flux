package com.flux.gateway;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class LocalConnectionRegistry {
    private final ConcurrentHashMap<SessionId, GatewayConnection> connections;
    private final AtomicLong totalRegistered;
    private final AtomicLong totalUnregistered;
    
    public LocalConnectionRegistry() {
        this.connections = new ConcurrentHashMap<>();
        this.totalRegistered = new AtomicLong(0);
        this.totalUnregistered = new AtomicLong(0);
    }
    
    /**
     * Register a new connection. Thread-safe via ConcurrentHashMap.
     */
    public void register(SessionId sessionId, GatewayConnection connection) {
        GatewayConnection existing = connections.putIfAbsent(sessionId, connection);
        if (existing != null) {
            throw new IllegalStateException("Duplicate session ID: " + sessionId);
        }
        totalRegistered.incrementAndGet();
        System.out.println("[REGISTRY] Registered session: " + sessionId + 
                           " (active: " + connections.size() + ")");
    }
    
    /**
     * Unregister a connection and cleanup resources.
     */
    public void unregister(SessionId sessionId) {
        GatewayConnection removed = connections.remove(sessionId);
        if (removed != null) {
            removed.close();
            totalUnregistered.incrementAndGet();
            System.out.println("[REGISTRY] Unregistered session: " + sessionId + 
                               " (active: " + connections.size() + ")");
        }
    }
    
    /**
     * Get a specific connection.
     */
    public GatewayConnection get(SessionId sessionId) {
        return connections.get(sessionId);
    }
    
    /**
     * Get all connections subscribed to a guild.
     */
    public Collection<GatewayConnection> getGuildMembers(GuildId guildId) {
        return connections.values().stream()
            .filter(conn -> conn.subscribedGuilds().contains(guildId))
            .toList();
    }
    
    /**
     * Get all active connections.
     */
    public Collection<GatewayConnection> getAllConnections() {
        return connections.values();
    }
    
    /**
     * Current number of active connections.
     */
    public int size() {
        return connections.size();
    }
    
    /**
     * Check for potential zombie leaks (connections not properly cleaned up).
     */
    public long getZombieLeakCount() {
        return totalRegistered.get() - totalUnregistered.get() - connections.size();
    }
    
    /**
     * Evict slow consumers (connections with full write queues).
     */
    public int evictSlowConsumers() {
        int evicted = 0;
        for (GatewayConnection conn : connections.values()) {
            if (conn.isSlowConsumer()) {
                System.out.println("[REGISTRY] Evicting slow consumer: " + conn.sessionId());
                unregister(conn.sessionId());
                evicted++;
            }
        }
        return evicted;
    }
}
