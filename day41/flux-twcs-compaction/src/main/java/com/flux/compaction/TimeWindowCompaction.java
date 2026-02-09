package com.flux.compaction;

import com.flux.storage.SSTable;

import java.util.*;
import java.util.stream.Collectors;

public class TimeWindowCompaction implements CompactionStrategy {
    private static final long WINDOW_SIZE_MS = 3600_000; // 1 hour
    private static final int MIN_THRESHOLD = 4;
    private final long ttlMs;
    
    public TimeWindowCompaction(long ttlMs) {
        this.ttlMs = ttlMs;
    }
    
    @Override
    public List<SSTable> selectForCompaction(List<SSTable> sstables) {
        // Remove expired SSTables first
        long cutoff = System.currentTimeMillis() - ttlMs;
        List<SSTable> active = sstables.stream()
            .filter(sst -> sst.createdAt() >= cutoff)
            .toList();
        
        if (active.size() < MIN_THRESHOLD) {
            return List.of();
        }
        
        // Group by time windows
        Map<Long, List<SSTable>> windows = active.stream()
            .collect(Collectors.groupingBy(sst -> sst.timeWindowBucket(WINDOW_SIZE_MS)));
        
        // Find window with most files (but NEVER merge across windows)
        return windows.values().stream()
            .filter(window -> window.size() >= MIN_THRESHOLD)
            .max(Comparator.comparingInt(List::size))
            .orElse(List.of());
    }
    
    public List<SSTable> selectExpired(List<SSTable> sstables) {
        long cutoff = System.currentTimeMillis() - ttlMs;
        return sstables.stream()
            .filter(sst -> sst.createdAt() < cutoff)
            .toList();
    }
    
    @Override
    public String name() {
        return "TWCS";
    }
}
