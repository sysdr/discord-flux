package com.flux.typing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThrottleGateTest {
    
    @Test
    void testThrottling() throws InterruptedException {
        ThrottleGate gate = new ThrottleGate();
        
        // First call should succeed
        assertTrue(gate.tryAcquire(1000), "First call should succeed");
        
        // Immediate second call should fail
        assertFalse(gate.tryAcquire(1000), "Immediate second call should be throttled");
        
        // After 3+ seconds, should succeed again
        Thread.sleep(3100);
        assertTrue(gate.tryAcquire(1000), "Call after throttle period should succeed");
    }
    
    @Test
    void testDifferentUsers() {
        ThrottleGate gate = new ThrottleGate();
        
        assertTrue(gate.tryAcquire(2000), "User 2000 first call");
        assertTrue(gate.tryAcquire(2001), "User 2001 first call");
        
        // Both should be throttled on second immediate call
        assertFalse(gate.tryAcquire(2000), "User 2000 throttled");
        assertFalse(gate.tryAcquire(2001), "User 2001 throttled");
    }
}
