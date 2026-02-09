package com.flux.compaction;

import com.flux.storage.SSTable;

import java.util.*;
import java.util.stream.Collectors;

public class SizeTieredCompaction implements CompactionStrategy {
    private static final int MIN_THRESHOLD = 4;
    private static final double SIZE_TOLERANCE = 0.5;
    
    @Override
    public List<SSTable> selectForCompaction(List<SSTable> sstables) {
        if (sstables.size() < MIN_THRESHOLD) {
            return List.of();
        }
        
        // Group by size buckets
        Map<Integer, List<SSTable>> buckets = sstables.stream()
            .collect(Collectors.groupingBy(this::getSizeBucket));
        
        // Find the bucket with the most files
        return buckets.values().stream()
            .filter(bucket -> bucket.size() >= MIN_THRESHOLD)
            .max(Comparator.comparingInt(List::size))
            .orElse(List.of());
    }
    
    private int getSizeBucket(SSTable sst) {
        long size = sst.sizeBytes();
        // Bucket: 0-1MB=0, 1-2MB=1, 2-4MB=2, 4-8MB=3, etc
        return (int) (Math.log(size) / Math.log(2));
    }
    
    @Override
    public String name() {
        return "STCS";
    }
}
