package com.flux.gateway.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics collection.
 */
public class Metrics {
    
    private static final Metrics INSTANCE = new Metrics();
    
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final Map<Byte, AtomicLong> opcodeCounters = new ConcurrentHashMap<>();
    
    private Metrics() {}
    
    public static Metrics getInstance() {
        return INSTANCE;
    }
    
    public void incrementConnections() {
        activeConnections.incrementAndGet();
    }
    
    public void decrementConnections() {
        activeConnections.updateAndGet(v -> Math.max(0, v - 1));
    }
    
    public long getActiveConnections() {
        return activeConnections.get();
    }
    
    public void recordOpcode(byte opcode) {
        opcodeCounters.computeIfAbsent(opcode, k -> new AtomicLong()).incrementAndGet();
    }
    
    public Map<Byte, Long> getOpcodeDistribution() {
        Map<Byte, Long> snapshot = new ConcurrentHashMap<>();
        opcodeCounters.forEach((k, v) -> snapshot.put(k, v.get()));
        return snapshot;
    }
    
    public void reset() {
        opcodeCounters.clear();
    }
}
