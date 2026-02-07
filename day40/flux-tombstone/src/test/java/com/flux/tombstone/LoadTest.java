package com.flux.tombstone;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class LoadTest {
    
    @Test
    void testConcurrentInserts() throws InterruptedException {
        MessageStore store = new MessageStore();
        int threads = 100;
        int messagesPerThread = 100;
        
        AtomicInteger successCount = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(threads);
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        long start = System.nanoTime();
        
        for (int i = 0; i < threads; i++) {
            int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        Message msg = new Message("load-test-" + threadId, 
                            "Message " + j);
                        store.insert(msg);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long elapsed = System.nanoTime() - start;
        
        executor.shutdown();
        store.shutdown();
        
        System.out.printf("✅ Load Test Complete:%n");
        System.out.printf("   Threads: %d%n", threads);
        System.out.printf("   Messages: %d%n", successCount.get());
        System.out.printf("   Duration: %d ms%n", TimeUnit.NANOSECONDS.toMillis(elapsed));
        System.out.printf("   Throughput: %.2f msg/sec%n", 
            successCount.get() / (elapsed / 1_000_000_000.0));
    }
    
    @Test
    void testConcurrentDeletesAndReads() throws InterruptedException {
        MessageStore store = new MessageStore();
        
        // Pre-populate
        ConcurrentHashMap<MessageId, Message> inserted = new ConcurrentHashMap<>();
        for (int i = 0; i < 1000; i++) {
            Message msg = new Message("channel", "Message " + i);
            store.insert(msg);
            inserted.put(msg.id(), msg);
        }
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(200);
        
        // Spawn 100 readers + 100 deleters
        for (int i = 0; i < 100; i++) {
            // Reader
            executor.submit(() -> {
                try {
                    for (var msg : inserted.values()) {
                        store.read(msg.id());
                    }
                } finally {
                    latch.countDown();
                }
            });
            
            // Deleter
            executor.submit(() -> {
                try {
                    for (var msg : inserted.values()) {
                        if (Math.random() < 0.5) {
                            store.delete(msg.id());
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        store.shutdown();
        
        System.out.println("✅ Concurrent delete/read test passed");
    }
}
