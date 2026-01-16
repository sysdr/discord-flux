package com.flux.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TimeoutWheelTest {
    private TimeoutWheel wheel;
    
    @BeforeEach
    void setUp() {
        wheel = new TimeoutWheel();
    }
    
    @Test
    void testScheduleAndExpire() {
        wheel.schedule("conn-1", 5);
        
        // Advance 4 slots - should not expire
        for (int i = 0; i < 4; i++) {
            Set<String> expired = wheel.advance();
            assertTrue(expired.isEmpty());
        }
        
        // Advance to slot 5 - should expire
        Set<String> expired = wheel.advance();
        assertEquals(1, expired.size());
        assertTrue(expired.contains("conn-1"));
    }
    
    @Test
    void testMultipleConnections() {
        wheel.schedule("conn-1", 10);
        wheel.schedule("conn-2", 10);
        wheel.schedule("conn-3", 20);
        
        // Advance 10 slots
        for (int i = 0; i < 9; i++) {
            wheel.advance();
        }
        
        Set<String> expired = wheel.advance();
        assertEquals(2, expired.size());
        assertTrue(expired.contains("conn-1"));
        assertTrue(expired.contains("conn-2"));
    }
    
    @Test
    void testReschedule() {
        wheel.schedule("conn-1", 5);
        wheel.advance();
        wheel.schedule("conn-1", 5); // Reschedule
        
        // Should expire after 5 more advances, not 4
        for (int i = 0; i < 4; i++) {
            Set<String> expired = wheel.advance();
            assertFalse(expired.contains("conn-1"));
        }
        
        Set<String> expired = wheel.advance();
        assertTrue(expired.contains("conn-1"));
    }
    
    @Test
    void testWheelWraparound() {
        wheel.schedule("conn-1", 59);
        
        // Advance 58 slots
        for (int i = 0; i < 58; i++) {
            wheel.advance();
        }
        
        // Should expire on next advance
        Set<String> expired = wheel.advance();
        assertTrue(expired.contains("conn-1"));
    }
}
