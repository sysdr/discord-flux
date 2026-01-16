package com.flux.gateway.concurrency.common;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class ServerMetrics {
    private final LongAdder totalConnections = new LongAdder();
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final LongAdder messagesReceived = new LongAdder();
    private final LongAdder messagesSent = new LongAdder();
    private final LongAdder bytesReceived = new LongAdder();
    private final LongAdder bytesSent = new LongAdder();
    private final long startTime = System.currentTimeMillis();
    
    public void connectionAccepted() {
        totalConnections.increment();
        activeConnections.incrementAndGet();
    }
    
    public void connectionClosed() {
        activeConnections.decrementAndGet();
    }
    
    public void messageReceived(int bytes) {
        messagesReceived.increment();
        bytesReceived.add(bytes);
    }
    
    public void messageSent(int bytes) {
        messagesSent.increment();
        bytesSent.add(bytes);
    }
    
    public MetricsSnapshot snapshot() {
        long uptime = System.currentTimeMillis() - startTime;
        return new MetricsSnapshot(
            totalConnections.sum(),
            activeConnections.get(),
            messagesReceived.sum(),
            messagesSent.sum(),
            bytesReceived.sum(),
            bytesSent.sum(),
            uptime
        );
    }
    
    public record MetricsSnapshot(
        long totalConnections,
        long activeConnections,
        long messagesReceived,
        long messagesSent,
        long bytesReceived,
        long bytesSent,
        long uptimeMs
    ) {
        public String toJson() {
            return String.format(
                "{\"totalConnections\":%d,\"activeConnections\":%d," +
                "\"messagesReceived\":%d,\"messagesSent\":%d," +
                "\"bytesReceived\":%d,\"bytesSent\":%d," +
                "\"uptimeMs\":%d,\"messagesPerSec\":%.2f}",
                totalConnections, activeConnections,
                messagesReceived, messagesSent,
                bytesReceived, bytesSent, uptimeMs,
                messagesReceived * 1000.0 / Math.max(uptimeMs, 1)
            );
        }
    }
}
