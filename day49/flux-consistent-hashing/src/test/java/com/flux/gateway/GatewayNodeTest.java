package com.flux.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayNodeTest {
    
    @Test
    void testValidNode() {
        GatewayNode node = new GatewayNode("node-01", "10.0.0.1", 9001);
        assertEquals("node-01", node.id());
        assertEquals("10.0.0.1", node.host());
        assertEquals(9001, node.port());
        assertEquals("10.0.0.1:9001", node.getAddress());
    }
    
    @Test
    void testInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> 
            new GatewayNode("", "10.0.0.1", 9001));
        assertThrows(IllegalArgumentException.class, () -> 
            new GatewayNode(null, "10.0.0.1", 9001));
    }
    
    @Test
    void testInvalidHost() {
        assertThrows(IllegalArgumentException.class, () -> 
            new GatewayNode("node-01", "", 9001));
        assertThrows(IllegalArgumentException.class, () -> 
            new GatewayNode("node-01", null, 9001));
    }
    
    @Test
    void testInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> 
            new GatewayNode("node-01", "10.0.0.1", 0));
        assertThrows(IllegalArgumentException.class, () -> 
            new GatewayNode("node-01", "10.0.0.1", -1));
        assertThrows(IllegalArgumentException.class, () -> 
            new GatewayNode("node-01", "10.0.0.1", 65536));
    }
}
