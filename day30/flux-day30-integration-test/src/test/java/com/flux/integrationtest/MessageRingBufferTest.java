package com.flux.integrationtest;

import com.flux.integrationtest.gateway.Message;
import com.flux.integrationtest.gateway.MessageRingBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageRingBufferTest {
    @Test
    void testOfferAndPoll() {
        MessageRingBuffer buffer = new MessageRingBuffer();
        Message msg = Message.chat(1, "test");
        
        assertTrue(buffer.offer(msg));
        assertEquals(1, buffer.size());
        
        Message retrieved = buffer.poll();
        assertNotNull(retrieved);
        assertEquals(msg.senderId(), retrieved.senderId());
        assertEquals(0, buffer.size());
    }
    
    @Test
    void testBufferFull() {
        MessageRingBuffer buffer = new MessageRingBuffer();
        
        // Fill buffer
        for (int i = 0; i < 1024; i++) {
            assertTrue(buffer.offer(Message.chat(i, "msg" + i)));
        }
        
        // Next offer should fail
        assertFalse(buffer.offer(Message.chat(9999, "overflow")));
    }
    
    @Test
    void testConcurrentAccess() throws InterruptedException {
        MessageRingBuffer buffer = new MessageRingBuffer();
        
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                buffer.offer(Message.chat(i, "msg" + i));
            }
        });
        
        Thread consumer = new Thread(() -> {
            int count = 0;
            while (count < 1000) {
                Message msg = buffer.poll();
                if (msg != null) count++;
            }
        });
        
        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        
        assertEquals(0, buffer.size());
    }
}
