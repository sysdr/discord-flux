package com.flux;

import com.flux.model.SnowflakeId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SnowflakeIdTest {
    
    @Test
    void testGenerate() {
        SnowflakeId id1 = SnowflakeId.generate();
        SnowflakeId id2 = SnowflakeId.generate();
        
        assertTrue(id2.value() > id1.value());
    }
    
    @Test
    void testTimestampExtraction() {
        long now = System.currentTimeMillis();
        SnowflakeId id = SnowflakeId.generate();
        long extracted = id.timestampMillis();
        
        assertTrue(Math.abs(extracted - now) < 100);
    }
    
    @Test
    void testComparable() {
        SnowflakeId id1 = SnowflakeId.generate();
        try { Thread.sleep(2); } catch (Exception e) {}
        SnowflakeId id2 = SnowflakeId.generate();
        
        assertTrue(id1.compareTo(id2) < 0);
    }
}
