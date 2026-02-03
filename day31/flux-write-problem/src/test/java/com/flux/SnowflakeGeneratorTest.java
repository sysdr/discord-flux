package com.flux;

import com.flux.core.SnowflakeGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeGeneratorTest {
    
    @Test
    void testIdGeneration() {
        var generator = new SnowflakeGenerator(1, 1);
        long id1 = generator.nextId();
        long id2 = generator.nextId();
        
        assertTrue(id2 > id1, "IDs should be monotonically increasing");
    }
    
    @Test
    void testExtraction() {
        var generator = new SnowflakeGenerator(5, 10);
        long id = generator.nextId();
        
        assertEquals(5, SnowflakeGenerator.extractDatacenterId(id));
        assertEquals(10, SnowflakeGenerator.extractWorkerId(id));
    }
    
    @Test
    void testUniqueness() throws InterruptedException {
        var generator = new SnowflakeGenerator(1, 1);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10000; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < 100; j++) {
                        ids.add(generator.nextId());
                    }
                });
            }
        }
        
        assertEquals(1_000_000, ids.size(), "All IDs should be unique");
    }
    
    @Test
    void testTimestampOrdering() {
        var generator = new SnowflakeGenerator(1, 1);
        long id1 = generator.nextId();
        long id2 = generator.nextId();
        
        long ts1 = SnowflakeGenerator.extractTimestamp(id1);
        long ts2 = SnowflakeGenerator.extractTimestamp(id2);
        
        assertTrue(ts2 >= ts1, "Timestamps should be ordered");
    }
}
