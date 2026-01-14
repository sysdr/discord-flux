package com.flux.gateway;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HeartbeatManagerTest {
    
    @Test
    void testConnectionRegistration() {
        ConnectionRegistry registry = new ConnectionRegistry();
        assertEquals(0, registry.getActiveCount());
    }
    
    @Test
    void testMetricsIncrement() {
        Metrics metrics = new Metrics();
        metrics.incrementHeartbeatsSent();
        metrics.incrementAcksReceived();
        
        assertEquals(1, metrics.getHeartbeatsSent());
        assertEquals(1, metrics.getAcksReceived());
    }
    
    @Test
    void testOpcodeEnumMapping() {
        assertEquals(Opcode.HEARTBEAT, Opcode.fromValue(1));
        assertEquals(Opcode.HEARTBEAT_ACK, Opcode.fromValue(11));
    }
}
