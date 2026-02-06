package com.flux;

import com.flux.generator.SnowflakeGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeGeneratorTest {

    @Test
    void testBasicIdGeneration() {
        SnowflakeGenerator generator = new SnowflakeGenerator(1);
        long id1 = generator.nextId();
        long id2 = generator.nextId();
        
        assertTrue(id2 > id1, "IDs should be monotonically increasing");
    }

    @Test
    void testIdUniqueness() {
        SnowflakeGenerator generator = new SnowflakeGenerator(1);
        Set<Long> ids = new HashSet<>();
        
        for (int i = 0; i < 1000; i++) {
            long id = generator.nextId();
            assertTrue(ids.add(id), "Duplicate ID generated: " + id);
        }
        
        assertEquals(1000, ids.size());
    }

    @Test
    void testConcurrentIdGeneration() throws InterruptedException {
        SnowflakeGenerator generator = new SnowflakeGenerator(1);
        int threadCount = 5;
        int idsPerThread = 200;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threadCount * idsPerThread);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < idsPerThread; j++) {
                        ids.add(generator.nextId());
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
        }

        assertEquals(threadCount * idsPerThread, ids.size(), "All IDs should be unique");
    }

    @Test
    void testTimestampExtraction() {
        SnowflakeGenerator generator = new SnowflakeGenerator(5);
        long beforeTimestamp = System.currentTimeMillis();
        long id = generator.nextId();
        long afterTimestamp = System.currentTimeMillis();
        
        long extractedTimestamp = SnowflakeGenerator.getTimestamp(id);
        
        assertTrue(extractedTimestamp >= beforeTimestamp && extractedTimestamp <= afterTimestamp,
                "Extracted timestamp should be within generation time window");
    }

    @Test
    void testWorkerIdExtraction() {
        long expectedWorkerId = 42;
        SnowflakeGenerator generator = new SnowflakeGenerator(expectedWorkerId);
        long id = generator.nextId();
        
        long extractedWorkerId = SnowflakeGenerator.getWorkerId(id);
        
        assertEquals(expectedWorkerId, extractedWorkerId);
    }

    @Test
    void testInvalidWorkerId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeGenerator(-1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeGenerator(1024));
    }
}
