package com.flux.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ReplayBufferTest {
    private ReplayBuffer buffer;
    
    @BeforeEach
    void setup() {
        buffer = new ReplayBuffer(8);
    }
    
    @Test
    void testBasicWriteAndRead() {
        byte[] msg1 = "Message 1".getBytes(StandardCharsets.UTF_8);
        byte[] msg2 = "Message 2".getBytes(StandardCharsets.UTF_8);
        
        long seq1 = buffer.write(msg1);
        long seq2 = buffer.write(msg2);
        
        assertEquals(0, seq1);
        assertEquals(1, seq2);
        assertEquals(2, buffer.size());
    }
    
    @Test
    void testCircularWrapAround() {
        for (int i = 0; i < 20; i++) {
            String msg = "Message " + i;
            buffer.write(msg.getBytes(StandardCharsets.UTF_8));
        }
        
        assertEquals(8, buffer.size());
        
        List<byte[]> messages = buffer.readFrom(-1);
        assertEquals(8, messages.size());
    }
    
    @Test
    void testSequenceReplay() {
        for (int i = 0; i < 5; i++) {
            String msg = "Message " + i;
            buffer.write(msg.getBytes(StandardCharsets.UTF_8));
        }
        
        List<byte[]> replay = buffer.readFrom(2);
        assertEquals(2, replay.size());
        
        String msg = new String(replay.get(0), StandardCharsets.UTF_8);
        assertTrue(msg.contains("Message 3"));
    }
    
    @Test
    void testConcurrentWrites() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1000);
        
        long start = System.nanoTime();
        
        for (int i = 0; i < 1000; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String msg = "Concurrent " + index;
                    buffer.write(msg.getBytes(StandardCharsets.UTF_8));
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long duration = System.nanoTime() - start;
        double opsPerSec = 1_000_000_000_000.0 / duration;
        
        System.out.printf("[PERF] Concurrent writes: %.2f ops/sec%n", opsPerSec);
        
        executor.shutdown();
        assertTrue(opsPerSec > 10_000, "Should handle >10K ops/sec");
    }
    
    @Test
    void testBufferEviction() {
        ReplayBuffer small = new ReplayBuffer(4);
        
        for (int i = 0; i < 10; i++) {
            small.write(("Message " + i).getBytes(StandardCharsets.UTF_8));
        }
        
        assertEquals(4, small.size());
        
        List<byte[]> messages = small.readFrom(-1);
        String last = new String(messages.get(messages.size() - 1), StandardCharsets.UTF_8);
        assertTrue(last.contains("Message 9"));
    }
}
