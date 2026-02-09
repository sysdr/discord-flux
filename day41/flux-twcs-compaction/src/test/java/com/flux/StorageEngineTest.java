package com.flux;

import com.flux.compaction.*;
import com.flux.model.Message;
import com.flux.storage.StorageEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class StorageEngineTest {
    
    @Test
    void testWriteAndRead(@TempDir Path tempDir) throws Exception {
        var compaction = new CompactionManager(
            new TimeWindowCompaction(86400_000),
            tempDir
        );
        var engine = new StorageEngine(tempDir, compaction);
        
        Message msg = Message.create(1L, 100L, "Hello World");
        engine.write(msg);
        
        Message retrieved = engine.read(msg.id());
        assertNotNull(retrieved);
        assertEquals("Hello World", retrieved.content());
    }
    
    @Test
    void testFlush(@TempDir Path tempDir) throws Exception {
        var compaction = new CompactionManager(
            new TimeWindowCompaction(86400_000),
            tempDir
        );
        var engine = new StorageEngine(tempDir, compaction);
        
        for (int i = 0; i < 100; i++) {
            engine.write(Message.create(1L, 100L, "Message " + i));
        }
        
        engine.flush();
        assertTrue(engine.sstableCount() > 0);
    }
}
