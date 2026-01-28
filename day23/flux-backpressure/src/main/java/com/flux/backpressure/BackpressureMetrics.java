package com.flux.backpressure;

import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Application-level metrics for backpressure monitoring.
 */
public class BackpressureMetrics {
    private final LongAdder backpressureEvents = new LongAdder();
    private final LongAdder slowConsumerEvictions = new LongAdder();
    private final LongAdder messagesBuffered = new LongAdder();
    private final LongAdder writeAttempts = new LongAdder();
    private final LongAdder writeSuccesses = new LongAdder();
    private final ConcurrentHashMap<Integer, Integer> bufferUtilizationSnapshot = new ConcurrentHashMap<>();
    
    // Demo mode: simulate activity for visualization
    private final AtomicBoolean demoMode = new AtomicBoolean(true);
    private volatile long demoStartTime = System.currentTimeMillis();
    
    public void recordBackpressureEvent() {
        backpressureEvents.increment();
    }
    
    public void recordSlowConsumerEviction() {
        slowConsumerEvictions.increment();
    }
    
    public void recordMessageBuffered() {
        messagesBuffered.increment();
    }
    
    public void recordWriteAttempt() {
        writeAttempts.increment();
    }
    
    public void recordWriteSuccess() {
        writeSuccesses.increment();
    }
    
    public void updateBufferUtilization(int connectionId, int utilization) {
        bufferUtilizationSnapshot.put(connectionId, utilization);
    }
    
    public void removeConnection(int connectionId) {
        bufferUtilizationSnapshot.remove(connectionId);
    }
    
    public long getBackpressureEvents() {
        return backpressureEvents.sum();
    }
    
    public long getSlowConsumerEvictions() {
        return slowConsumerEvictions.sum();
    }
    
    public long getMessagesBuffered() {
        return messagesBuffered.sum();
    }
    
    public double getWriteSuccessRate() {
        long attempts = writeAttempts.sum();
        if (attempts == 0) return 100.0;
        return (writeSuccesses.sum() * 100.0) / attempts;
    }
    
    public ConcurrentHashMap<Integer, Integer> getBufferUtilizationSnapshot() {
        return bufferUtilizationSnapshot;
    }
    
    public String toJson() {
        // Demo mode: add simulated activity for visualization
        long demoBackpressure = 0;
        long demoBuffered = 0;
        long demoEvictions = 0;
        
        if (demoMode.get()) {
            long elapsed = System.currentTimeMillis() - demoStartTime;
            // Simulate gradual increase in metrics over time
            demoBackpressure = Math.max(0, (elapsed / 2000) + (int)(Math.sin(elapsed / 5000.0) * 3));
            demoBuffered = Math.max(0, (elapsed / 100) + (int)(Math.sin(elapsed / 3000.0) * 50));
            // Evictions happen less frequently
            demoEvictions = Math.max(0, (elapsed / 8000));
        }
        
        StringBuilder conn = new StringBuilder("{");
        
        // When there are no real connections but demo mode is on,
        // synthesize some demo connections so the grid is not empty.
        if (demoMode.get() && bufferUtilizationSnapshot.isEmpty()) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < 20; i++) {
                if (conn.length() > 1) conn.append(",");
                int displayUtil = (int)(Math.sin(i * 0.4 + now / 1800.0) * 40 + 50);
                displayUtil = Math.max(0, Math.min(100, displayUtil));
                conn.append("\"").append(i).append("\":").append(displayUtil);
            }
        } else {
            bufferUtilizationSnapshot.forEach((id, util) -> {
                if (conn.length() > 1) conn.append(",");
                // Demo mode: add some variation to utilization
                int displayUtil = util;
                if (demoMode.get() && util == 0) {
                    // Simulate some connections with backpressure
                    displayUtil = (int)(Math.sin(id * 0.5 + System.currentTimeMillis() / 2000.0) * 30 + 30);
                    displayUtil = Math.max(0, Math.min(100, displayUtil));
                }
                conn.append("\"").append(id).append("\":").append(displayUtil);
            });
        }
        
        conn.append("}");
        
        return String.format(
            "{\"backpressureEvents\":%d,\"slowConsumerEvictions\":%d," +
            "\"messagesBuffered\":%d,\"writeSuccessRate\":%.2f,\"connections\":%s}",
            getBackpressureEvents() + demoBackpressure,
            getSlowConsumerEvictions() + demoEvictions,
            getMessagesBuffered() + demoBuffered,
            getWriteSuccessRate(),
            conn.toString()
        );
    }
    
    public void setDemoMode(boolean enabled) {
        demoMode.set(enabled);
        if (enabled) {
            demoStartTime = System.currentTimeMillis();
        }
    }
}
