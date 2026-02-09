package com.flux.grpc;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdGeneratorTest {
    
    @Test
    void testUniqueIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        Set<Long> ids = new HashSet<>();
        
        for (int i = 0; i < 10000; i++) {
            long id = generator.nextId();
            assertTrue(ids.add(id), "Duplicate ID generated: " + id);
        }
    }
    
    @Test
    void testIncreasingIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        long prev = generator.nextId();
        
        for (int i = 0; i < 1000; i++) {
            long current = generator.nextId();
            assertTrue(current > prev, "IDs not increasing");
            prev = current;
        }
    }
}
