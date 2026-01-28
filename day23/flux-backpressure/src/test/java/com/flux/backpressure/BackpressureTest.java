package com.flux.backpressure;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for backpressure components.
 */
public class BackpressureTest {
    
    @Test
    public void testRingBufferOfferAndPoll() {
        RingBuffer buffer = new RingBuffer(8); // small buffer for testing
        
        ByteBuffer msg1 = ByteBuffer.wrap("msg1".getBytes());
        ByteBuffer msg2 = ByteBuffer.wrap("msg2".getBytes());
        
        assertTrue(buffer.offer(msg1));
        assertTrue(buffer.offer(msg2));
        assertEquals(2, buffer.size());
        
        ByteBuffer polled1 = buffer.poll();
        assertNotNull(polled1);
        
        ByteBuffer polled2 = buffer.poll();
        assertNotNull(polled2);
        
        assertNull(buffer.poll()); // empty
    }
    
    @Test
    public void testRingBufferFull() {
        RingBuffer buffer = new RingBuffer(4);
        
        ByteBuffer msg = ByteBuffer.wrap("test".getBytes());
        
        assertTrue(buffer.offer(msg));
        assertTrue(buffer.offer(msg));
        assertTrue(buffer.offer(msg));
        assertFalse(buffer.offer(msg)); // should fail, buffer full
        
        assertEquals(3, buffer.size());
    }
    
    @Test
    public void testRingBufferUtilization() {
        RingBuffer buffer = new RingBuffer(16);
        
        ByteBuffer msg = ByteBuffer.wrap("test".getBytes());
        
        assertEquals(0, buffer.getUtilization());
        
        buffer.offer(msg);
        buffer.offer(msg);
        buffer.offer(msg);
        buffer.offer(msg); // 4/16 = 25%
        
        assertEquals(25, buffer.getUtilization());
    }
    
    @Test
    public void testBackpressureMetrics() {
        BackpressureMetrics metrics = new BackpressureMetrics();
        
        assertEquals(0, metrics.getBackpressureEvents());
        
        metrics.recordBackpressureEvent();
        metrics.recordBackpressureEvent();
        
        assertEquals(2, metrics.getBackpressureEvents());
        
        metrics.recordSlowConsumerEviction();
        assertEquals(1, metrics.getSlowConsumerEvictions());
    }
}
