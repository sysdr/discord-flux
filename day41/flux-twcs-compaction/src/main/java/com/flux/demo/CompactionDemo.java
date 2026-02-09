package com.flux.demo;

import com.flux.compaction.*;
import com.flux.model.Message;
import com.flux.storage.StorageEngine;

import java.nio.file.Paths;
import java.util.Random;

public class CompactionDemo {
    public static void main(String[] args) throws Exception {
        System.out.println("Running STCS vs TWCS comparison...");
        System.out.println();
        
        // Run STCS
        System.out.println("=== Size-Tiered Compaction Strategy ===");
        runDemo("data/stcs", new SizeTieredCompaction());
        
        System.out.println();
        
        // Run TWCS
        System.out.println("=== Time-Window Compaction Strategy ===");
        runDemo("data/twcs", new TimeWindowCompaction(86400_000));
    }
    
    private static void runDemo(String dataPath, CompactionStrategy strategy) throws Exception {
        var compactionMgr = new CompactionManager(strategy, Paths.get(dataPath));
        var engine = new StorageEngine(Paths.get(dataPath), compactionMgr);
        
        Random rand = new Random();
        int messageCount = 10000;
        
        System.out.println("Writing " + messageCount + " messages...");
        long startWrite = System.nanoTime();
        
        for (int i = 0; i < messageCount; i++) {
            Message msg = Message.create(
                rand.nextInt(100),
                rand.nextInt(1000),
                "Message " + i + " with some content"
            );
            engine.write(msg);
            
            if (i % 1000 == 0) {
                engine.flush();
            }
        }
        engine.flush();
        
        long writeTime = (System.nanoTime() - startWrite) / 1_000_000;
        System.out.println("✓ Write time: " + writeTime + " ms");
        System.out.println("✓ SSTables before compaction: " + engine.sstableCount());
        
        // Run compaction
        System.out.println("Running compaction...");
        long startCompact = System.nanoTime();
        
        for (int i = 0; i < 3; i++) {
            engine.compact();
        }
        
        long compactTime = (System.nanoTime() - startCompact) / 1_000_000;
        System.out.println("✓ Compaction time: " + compactTime + " ms");
        System.out.println("✓ SSTables after compaction: " + engine.sstableCount());
        System.out.println("✓ Write amplification: " + 
            String.format("%.1f", compactionMgr.writeAmplification()) + "x");
        System.out.println("✓ Total I/O: " + 
            (compactionMgr.totalBytesRead() + compactionMgr.totalBytesWritten()) / 1024 / 1024 + " MB");
    }
}
