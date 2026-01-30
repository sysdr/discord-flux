package com.flux.ringbuffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class RingBufferTest {
    
    @Test
    void testBasicWriteRead() {
        RingBuffer buffer = new RingBuffer(16, "test-client");
        GuildEvent event = GuildEvent.create(1, "guild-1", "test message");
        
        assertTrue(buffer.tryWrite(event));
        assertEquals(1, buffer.size());
        
        BufferSlot slot = buffer.tryRead();
        assertNotNull(slot);
        assertEquals(1, slot.eventId());
        assertEquals("test message", slot.payload());
        
        assertEquals(0, buffer.size());
    }
    
    @Test
    void testBufferFull() {
        RingBuffer buffer = new RingBuffer(4, "test-client");
        
        // Fill buffer
        for (int i = 0; i < 4; i++) {
            assertTrue(buffer.tryWrite(GuildEvent.create(i, "guild-1", "msg " + i)));
        }
        
        // Next write should fail (buffer full)
        assertFalse(buffer.tryWrite(GuildEvent.create(99, "guild-1", "overflow")));
        assertEquals(1, buffer.getDroppedCount());
    }
    
    @Test
    void testUtilization() {
        RingBuffer buffer = new RingBuffer(16, "test-client");
        
        assertEquals(0, buffer.utilizationPercent());
        
        // Fill 50%
        for (int i = 0; i < 8; i++) {
            buffer.tryWrite(GuildEvent.create(i, "guild-1", "msg"));
        }
        
        assertEquals(50, buffer.utilizationPercent());
    }
    
    @Test
    void testConcurrentWriteRead() throws InterruptedException {
        // Buffer must hold all writes so reader can catch up (power of 2 >= numWrites)
        int numWrites = 10000;
        RingBuffer buffer = new RingBuffer(16384, "test-client");
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successfulWrites = new AtomicInteger(0);
        AtomicInteger successfulReads = new AtomicInteger(0);
        
        // Writer thread
        Thread writer = Thread.ofVirtual().start(() -> {
            for (int i = 0; i < numWrites; i++) {
                if (buffer.tryWrite(GuildEvent.create(i, "guild-1", "msg"))) {
                    successfulWrites.incrementAndGet();
                }
            }
            latch.countDown();
        });
        
        // Reader thread - read until we've read all that writer wrote
        Thread reader = Thread.ofVirtual().start(() -> {
            while (successfulReads.get() < numWrites) {
                if (buffer.tryRead() != null) {
                    successfulReads.incrementAndGet();
                }
            }
            latch.countDown();
        });
        
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        
        // All writes should eventually be read
        assertEquals(successfulWrites.get(), successfulReads.get());
    }
    
    @RepeatedTest(10)
    void testNoLostMessages() throws InterruptedException {
        RingBuffer buffer = new RingBuffer(64, "test-client");
        int numMessages = 1000;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger readCount = new AtomicInteger(0);
        
        // Slow reader
        Thread.ofVirtual().start(() -> {
            while (readCount.get() < numMessages) {
                BufferSlot slot = buffer.tryRead();
                if (slot != null) {
                    readCount.incrementAndGet();
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    break;
                }
            }
            latch.countDown();
        });
        
        // Fast writer
        int written = 0;
        for (int i = 0; i < numMessages; i++) {
            while (!buffer.tryWrite(GuildEvent.create(i, "guild-1", "msg " + i))) {
                Thread.onSpinWait();  // Wait for space
            }
            written++;
        }
        
        latch.await(10, TimeUnit.SECONDS);
        assertEquals(numMessages, readCount.get());
    }
}
