package com.flux.loadtest;

import com.flux.loadtest.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCollectorTest {
    
    @Test
    void testConnectionMetrics() {
        MetricsCollector metrics = new MetricsCollector();
        
        metrics.recordConnectionAttempt(1, true);
        metrics.recordConnectionAttempt(2, true);
        metrics.recordConnectionAttempt(3, false);
        
        var snapshot = metrics.getSnapshot();
        
        assertEquals(3, snapshot.totalAttempts());
        assertEquals(2, snapshot.successfulConnections());
        assertEquals(1, snapshot.failedConnections());
        assertEquals(66.67, snapshot.successRate(), 0.01);
    }
    
    @Test
    void testMessageCounters() {
        MetricsCollector metrics = new MetricsCollector();
        
        for (int i = 0; i < 1000; i++) {
            metrics.recordMessageSent(i);
            metrics.recordMessageReceived(i);
        }
        
        var snapshot = metrics.getSnapshot();
        
        assertEquals(1000, snapshot.messagesSent());
        assertEquals(1000, snapshot.messagesReceived());
    }
}
