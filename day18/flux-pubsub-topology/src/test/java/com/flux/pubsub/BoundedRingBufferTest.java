package com.flux.pubsub;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BoundedRingBufferTest {
    
    @Test
    void testOfferAndPoll() {
        var buffer = new BoundedRingBuffer(4);
        
        byte[] msg1 = "msg1".getBytes(StandardCharsets.UTF_8);
        byte[] msg2 = "msg2".getBytes(StandardCharsets.UTF_8);
        
        assertTrue(buffer.offer(msg1));
        assertTrue(buffer.offer(msg2));
        assertEquals(2, buffer.size());
        
        assertArrayEquals(msg1, buffer.poll());
        assertArrayEquals(msg2, buffer.poll());
        assertTrue(buffer.isEmpty());
    }
    
    @Test
    void testBufferFull() {
        var buffer = new BoundedRingBuffer(3);
        
        assertTrue(buffer.offer("1".getBytes()));
        assertTrue(buffer.offer("2".getBytes()));
        assertFalse(buffer.offer("3".getBytes())); // Should fail (n-1 capacity)
        
        assertEquals(1, buffer.droppedCount());
    }
    
    @Test
    void testWrapAround() {
        var buffer = new BoundedRingBuffer(4);
        
        // Fill and drain
        buffer.offer("a".getBytes());
        buffer.offer("b".getBytes());
        buffer.poll();
        buffer.poll();
        
        // Add more (should wrap around)
        buffer.offer("c".getBytes());
        buffer.offer("d".getBytes());
        
        assertEquals(2, buffer.size());
    }
}
