package com.flux.snowflake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

class SnowflakeGeneratorTest {
    
    @Test
    void testBasicIdGeneration() {
        SnowflakeGenerator generator = new SnowflakeGenerator(1);
        long id = generator.nextId();
        
        assertTrue(id > 0, "ID should be positive");
        
        SnowflakeId parsed = SnowflakeGenerator.parse(id);
        assertEquals(1, parsed.workerId(), "Worker ID should match");
        assertEquals(0, parsed.sequence(), "First sequence should be 0");
    }
    
    @Test
    void testSequentialIds() {
        SnowflakeGenerator generator = new SnowflakeGenerator(5);
        
        long id1 = generator.nextId();
        long id2 = generator.nextId();
        
        assertTrue(id2 > id1, "IDs should be monotonically increasing");
    }
    
    @Test
    void testUniqueness() {
        SnowflakeGenerator generator = new SnowflakeGenerator(10);
        Set<Long> ids = new HashSet<>();
        
        int count = 10000;
        for (int i = 0; i < count; i++) {
            long id = generator.nextId();
            assertTrue(ids.add(id), "Duplicate ID detected: " + id);
        }
        
        assertEquals(count, ids.size(), "Should generate unique IDs");
    }
    
    @Test
    void testConcurrentGeneration() throws InterruptedException {
        SnowflakeGenerator generator = new SnowflakeGenerator(42);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        
        int threadCount = 10;
        int idsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger duplicates = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                for (int j = 0; j < idsPerThread; j++) {
                    long id = generator.nextId();
                    if (!ids.add(id)) {
                        duplicates.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        
        latch.await();
        
        assertEquals(0, duplicates.get(), "No duplicates should occur");
        assertEquals(threadCount * idsPerThread, ids.size(), "All IDs should be unique");
    }
    
    @Test
    void testWorkerIdBounds() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeGenerator(-1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeGenerator(1024));
        
        // Valid boundaries
        assertDoesNotThrow(() -> new SnowflakeGenerator(0));
        assertDoesNotThrow(() -> new SnowflakeGenerator(1023));
    }
    
    @Test
    void testIdParsing() {
        SnowflakeGenerator generator = new SnowflakeGenerator(123);
        long id = generator.nextId();
        
        SnowflakeId parsed = SnowflakeGenerator.parse(id);
        
        assertEquals(123, parsed.workerId(), "Worker ID should be preserved");
        assertTrue(parsed.timestamp() > 0, "Timestamp should be valid");
        assertTrue(parsed.sequence() >= 0 && parsed.sequence() < 4096, 
                   "Sequence should be in valid range");
    }
    
    @Test
    void testHighThroughput() {
        SnowflakeGenerator generator = new SnowflakeGenerator(99);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        
        long start = System.nanoTime();
        int targetIds = 10000;
        
        for (int i = 0; i < targetIds; i++) {
            ids.add(generator.nextId());
        }
        
        long elapsed = System.nanoTime() - start;
        double throughput = targetIds / (elapsed / 1_000_000_000.0);
        
        assertEquals(targetIds, ids.size(), "All IDs should be unique");
        assertTrue(throughput > 1_000, "Should generate > 1K IDs/sec, got: " + (int)throughput);
    }
}
