package com.flux.compaction;

import com.flux.model.Message;
import com.flux.model.SnowflakeId;
import com.flux.storage.SSTable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class CompactionManager {
    private final CompactionStrategy strategy;
    private final Path outputDir;
    private final AtomicLong totalBytesRead = new AtomicLong(0);
    private final AtomicLong totalBytesWritten = new AtomicLong(0);
    private final AtomicLong compactionCount = new AtomicLong(0);
    
    public CompactionManager(CompactionStrategy strategy, Path outputDir) {
        this.strategy = strategy;
        this.outputDir = outputDir;
    }
    
    public List<SSTable> compact(List<SSTable> sstables) throws IOException {
        List<SSTable> toCompact = strategy.selectForCompaction(sstables);
        
        if (toCompact.isEmpty()) {
            return sstables;
        }
        
        compactionCount.incrementAndGet();
        
        // Read all messages from selected SSTables
        SortedMap<SnowflakeId, Message> merged = new TreeMap<>();
        for (SSTable sst : toCompact) {
            totalBytesRead.addAndGet(sst.sizeBytes());
            for (Message msg : sst.read()) {
                merged.put(msg.id(), msg);
            }
        }
        
        // Write merged SSTable
        SSTable newSST = SSTable.write(outputDir, merged);
        totalBytesWritten.addAndGet(newSST.sizeBytes());
        
        // Delete old SSTables
        for (SSTable sst : toCompact) {
            sst.delete();
        }
        
        // Return updated list
        List<SSTable> remaining = new ArrayList<>(sstables);
        remaining.removeAll(toCompact);
        remaining.add(newSST);
        return remaining;
    }
    
    public void deleteExpired(List<SSTable> sstables) throws IOException {
        if (strategy instanceof TimeWindowCompaction twcs) {
            List<SSTable> expired = twcs.selectExpired(sstables);
            for (SSTable sst : expired) {
                sst.delete();
                sstables.remove(sst);
            }
        }
    }
    
    public double writeAmplification() {
        long bytesWritten = totalBytesWritten.get();
        long userBytes = bytesWritten / Math.max(1, compactionCount.get());
        return bytesWritten / (double) Math.max(1, userBytes);
    }
    
    public long totalBytesRead() { return totalBytesRead.get(); }
    public long totalBytesWritten() { return totalBytesWritten.get(); }
    public long compactionCount() { return compactionCount.get(); }
    
    public void reset() {
        totalBytesRead.set(0);
        totalBytesWritten.set(0);
        compactionCount.set(0);
    }
}
