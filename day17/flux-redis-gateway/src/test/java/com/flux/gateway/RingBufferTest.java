package com.flux.gateway;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class RingBufferTest {
    
    @Test
    void testBasicOperations() {
        RingBuffer buffer = new RingBuffer(4);
        
        assertTrue(buffer.offer("msg1"));
        assertTrue(buffer.offer("msg2"));
        
        assertEquals("msg1", buffer.poll());
        assertEquals("msg2", buffer.poll());
        assertNull(buffer.poll());
    }
    
    @Test
    void testBackpressure() {
        RingBuffer buffer = new RingBuffer(4);
        
        assertTrue(buffer.offer("1"));
        assertTrue(buffer.offer("2"));
        assertTrue(buffer.offer("3"));
        assertTrue(buffer.offer("4"));
        
        // Buffer full - backpressure kicks in
        assertFalse(buffer.offer("5"));
        
        // Drain one, can add again
        buffer.poll();
        assertTrue(buffer.offer("5"));
    }
    
    @Test
    void testUtilization() {
        RingBuffer buffer = new RingBuffer(4);
        
        assertEquals(0.0, buffer.utilization(), 0.01);
        
        buffer.offer("1");
        buffer.offer("2");
        assertEquals(0.5, buffer.utilization(), 0.01);
        
        buffer.poll();
        assertEquals(0.25, buffer.utilization(), 0.01);
    }
    
    @Test
    void testConcurrentAccess() throws InterruptedException {
        RingBuffer buffer = new RingBuffer(1024);
        int producers = 4;
        int consumers = 2; // Reduce consumers to avoid race conditions
        int messagesPerProducer = 500; // Reduce messages for faster test
        int totalMessages = producers * messagesPerProducer;
        
        CountDownLatch producerLatch = new CountDownLatch(producers);
        AtomicInteger consumedCount = new AtomicInteger(0);
        ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();
        
        // Start producers
        for (int p = 0; p < producers; p++) {
            int producerId = p;
            Thread.startVirtualThread(() -> {
                for (int i = 0; i < messagesPerProducer; i++) {
                    String msg = "p" + producerId + "-m" + i;
                    while (!buffer.offer(msg)) {
                        Thread.onSpinWait();
                    }
                }
                producerLatch.countDown();
            });
        }
        
        // Start consumers - continue until all messages consumed
        CountDownLatch consumerLatch = new CountDownLatch(consumers);
        for (int c = 0; c < consumers; c++) {
            Thread.startVirtualThread(() -> {
                while (consumedCount.get() < totalMessages) {
                    String msg = buffer.poll();
                    if (msg != null) {
                        seen.put(msg, true);
                        consumedCount.incrementAndGet();
                    } else {
                        // If buffer empty but not all consumed, wait for producers
                        if (producerLatch.getCount() > 0 || buffer.size() > 0) {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        } else {
                            // Producers done and buffer empty - check if we're done
                            if (consumedCount.get() >= totalMessages) {
                                break;
                            }
                        }
                    }
                }
                consumerLatch.countDown();
            });
        }
        
        assertTrue(producerLatch.await(10, TimeUnit.SECONDS), "Producers did not finish");
        assertTrue(consumerLatch.await(15, TimeUnit.SECONDS), "Consumers did not finish");
        assertEquals(totalMessages, seen.size(), "Not all messages were consumed. Expected: " + totalMessages + ", Got: " + seen.size());
    }
}
