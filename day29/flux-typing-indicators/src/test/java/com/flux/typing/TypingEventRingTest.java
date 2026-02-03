package com.flux.typing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TypingEventRingTest {
    
    @Test
    void testPublishAndCollect() {
        TypingEventRing ring = new TypingEventRing(1024);
        
        // Publish events
        ring.publish(100, 1001);
        ring.publish(101, 1001);
        ring.publish(102, 1002); // Different channel
        
        // Collect for channel 1001
        long[] output = new long[10];
        int[] count = new int[1];
        ring.collectActiveTypers(1001, output, count);
        
        assertEquals(2, count[0], "Should have 2 typers in channel 1001");
        assertTrue(contains(output, count[0], 100), "Should contain user 100");
        assertTrue(contains(output, count[0], 101), "Should contain user 101");
    }
    
    @Test
    void testExpiration() throws InterruptedException {
        TypingEventRing ring = new TypingEventRing(1024);
        
        ring.publish(200, 2001);
        
        // Wait 6 seconds (TTL is 5 seconds)
        Thread.sleep(6000);
        
        long[] output = new long[10];
        int[] count = new int[1];
        ring.collectActiveTypers(2001, output, count);
        
        assertEquals(0, count[0], "Events should have expired after 6 seconds");
    }
    
    @Test
    void testDeduplication() {
        TypingEventRing ring = new TypingEventRing(1024);
        
        // Same user types multiple times
        ring.publish(300, 3001);
        ring.publish(300, 3001);
        ring.publish(300, 3001);
        
        long[] output = new long[10];
        int[] count = new int[1];
        ring.collectActiveTypers(3001, output, count);
        
        assertEquals(1, count[0], "Should deduplicate same user");
    }
    
    private boolean contains(long[] array, int length, long value) {
        for (int i = 0; i < length; i++) {
            if (array[i] == value) return true;
        }
        return false;
    }
}
