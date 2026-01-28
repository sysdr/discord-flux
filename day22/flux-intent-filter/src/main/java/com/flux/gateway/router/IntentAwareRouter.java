package com.flux.gateway.router;

import com.flux.gateway.connection.GatewayConnection;
import com.flux.gateway.model.GatewayEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class IntentAwareRouter {
    private final ConcurrentHashMap<String, GatewayConnection> connections = new ConcurrentHashMap<>();
    
    // Global metrics
    private final LongAdder totalEventsProcessed = new LongAdder();
    private final LongAdder totalEventsSent = new LongAdder();
    private final LongAdder totalEventsFiltered = new LongAdder();
    private final LongAdder totalBandwidthSaved = new LongAdder();
    
    // Performance tracking
    private volatile long lastCheckNanos = 0;

    public void registerConnection(GatewayConnection connection) {
        connections.put(connection.getUserId(), connection);
    }

    public void unregisterConnection(String userId) {
        connections.remove(userId);
    }

    public GatewayConnection getConnection(String userId) {
        return connections.get(userId);
    }

    public int getConnectionCount() {
        return connections.size();
    }

    public java.util.Set<String> getConnectionIds() {
        return java.util.Set.copyOf(connections.keySet());
    }

    public void dispatch(GatewayEvent event, Set<String> targetUserIds) {
        totalEventsProcessed.increment();
        long eventIntent = event.requiredIntent();
        
        for (var userId : targetUserIds) {
            var conn = connections.get(userId);
            if (conn == null) continue;
            
            // Start timing for performance measurement
            long startNanos = System.nanoTime();
            
            // Fast-path: single bitwise AND
            boolean shouldSend = (conn.getIntents() & eventIntent) != 0;
            
            // Track check latency
            lastCheckNanos = System.nanoTime() - startNanos;
            
            if (shouldSend) {
                conn.send(event);
                totalEventsSent.increment();
            } else {
                conn.filterEvent(event);
                totalEventsFiltered.increment();
                totalBandwidthSaved.add(event.estimatedSize());
            }
        }
    }

    public void broadcast(GatewayEvent event) {
        totalEventsProcessed.increment();
        long eventIntent = event.requiredIntent();
        
        for (var conn : connections.values()) {
            long startNanos = System.nanoTime();
            boolean shouldSend = (conn.getIntents() & eventIntent) != 0;
            lastCheckNanos = System.nanoTime() - startNanos;
            
            if (shouldSend) {
                conn.send(event);
                totalEventsSent.increment();
            } else {
                conn.filterEvent(event);
                totalEventsFiltered.increment();
                totalBandwidthSaved.add(event.estimatedSize());
            }
        }
    }

    public RouterMetrics getMetrics() {
        long processed = totalEventsProcessed.sum();
        long sent = totalEventsSent.sum();
        long filtered = totalEventsFiltered.sum();
        long bandwidthSaved = totalBandwidthSaved.sum();
        long decisions = sent + filtered;
        double filterRate = decisions > 0 ? (filtered * 100.0 / decisions) : 0.0;
        
        return new RouterMetrics(
            processed,
            sent,
            filtered,
            filterRate,
            bandwidthSaved,
            lastCheckNanos,
            connections.size()
        );
    }

    public void resetMetrics() {
        totalEventsProcessed.reset();
        totalEventsSent.reset();
        totalEventsFiltered.reset();
        totalBandwidthSaved.reset();
        
        for (var conn : connections.values()) {
            conn.resetMetrics();
        }
    }

    public record RouterMetrics(
        long totalEventsProcessed,
        long totalEventsSent,
        long totalEventsFiltered,
        double filterRate,
        long bandwidthSaved,
        long lastCheckNanos,
        int activeConnections
    ) {
        public String toJson() {
            return String.format(
                "{\"processed\":%d,\"sent\":%d,\"filtered\":%d," +
                "\"filterRate\":%.2f,\"bandwidthSaved\":%d," +
                "\"checkLatency\":%d,\"connections\":%d}",
                totalEventsProcessed, totalEventsSent, totalEventsFiltered,
                filterRate, bandwidthSaved, lastCheckNanos, activeConnections
            );
        }
    }
}
