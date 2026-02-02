package com.flux.presence;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionRingBufferTest {
    
    @Test
    void testBasicOfferAndPoll() {
        ConnectionRingBuffer buffer = new ConnectionRingBuffer(16);
        
        ByteBuffer msg1 = ByteBuffer.wrap("test1".getBytes());
        ByteBuffer msg2 = ByteBuffer.wrap("test2".getBytes());
        
        assertTrue(buffer.offer(msg1));
        assertTrue(buffer.offer(msg2));
        
        assertEquals(2, buffer.size());
        
        ByteBuffer retrieved1 = buffer.poll();
        assertNotNull(retrieved1);
        
        ByteBuffer retrieved2 = buffer.poll();
        assertNotNull(retrieved2);
        
        assertNull(buffer.poll()); // Empty
    }
    
    @Test
    void testRingBufferOverflow() {
        ConnectionRingBuffer buffer = new ConnectionRingBuffer(4);
        
        for (int i = 0; i < 10; i++) {
            buffer.offer(ByteBuffer.wrap(("msg" + i).getBytes()));
        }
        
        // Should have dropped some messages
        assertTrue(buffer.getDroppedCount() > 0);
        System.out.println("Dropped: " + buffer.getDroppedCount() + " messages");
    }
    
    @Test
    void testConcurrentProducers() throws Exception {
        ConnectionRingBuffer buffer = new ConnectionRingBuffer(1024);
        int producerCount = 10;
        int messagesPerProducer = 1000;
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(producerCount);
        
        for (int p = 0; p < producerCount; p++) {
            final int producerId = p;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < messagesPerProducer; i++) {
                        ByteBuffer msg = ByteBuffer.wrap(("P" + producerId + "-M" + i).getBytes());
                        buffer.offer(msg);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        
        long totalOffered = buffer.getOfferedCount();
        assertEquals(producerCount * messagesPerProducer, totalOffered);
        
        System.out.println("Offered: " + totalOffered);
        System.out.println("Dropped: " + buffer.getDroppedCount());
        System.out.println("Drop rate: " + (buffer.getDropRate() * 100) + "%");
        
        executor.shutdown();
    }
}
