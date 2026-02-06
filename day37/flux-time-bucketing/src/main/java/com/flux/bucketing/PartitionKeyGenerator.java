package com.flux.bucketing;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Deterministic time-bucket calculator for Cassandra partition keys.
 * Uses integer division to convert epoch milliseconds into fixed-width buckets.
 */
public class PartitionKeyGenerator {
    
    private static final long BUCKET_SIZE_MS = 10L * 24 * 60 * 60 * 1000; // 10 days
    private static final long EPOCH_START = 1704067200000L; // 2024-01-01 00:00:00 UTC
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;
    
    /**
     * Calculate bucket ID for a given timestamp.
     * Bucket 0 starts at EPOCH_START.
     */
    public static int calculateBucket(long timestampMs) {
        long offsetMs = timestampMs - EPOCH_START;
        if (offsetMs < 0) {
            throw new IllegalArgumentException("Timestamp before epoch start: " + timestampMs);
        }
        return (int) (offsetMs / BUCKET_SIZE_MS);
    }
    
    /**
     * Get the start timestamp (inclusive) of a bucket.
     */
    public static long bucketStartTime(int bucketId) {
        return EPOCH_START + (bucketId * BUCKET_SIZE_MS);
    }
    
    /**
     * Get the end timestamp (exclusive) of a bucket.
     */
    public static long bucketEndTime(int bucketId) {
        return bucketStartTime(bucketId + 1);
    }
    
    /**
     * Calculate which buckets overlap a time range [startMs, endMs).
     * Used for multi-partition queries.
     */
    public static List<Integer> bucketsForRange(long startMs, long endMs) {
        if (startMs > endMs) {
            throw new IllegalArgumentException("Start time after end time");
        }
        int startBucket = calculateBucket(startMs);
        int endBucket = calculateBucket(endMs);
        return IntStream.rangeClosed(startBucket, endBucket)
                        .boxed()
                        .toList();
    }
    
    /**
     * Format a bucket ID as a human-readable date range.
     */
    public static String formatBucket(int bucketId) {
        Instant start = Instant.ofEpochMilli(bucketStartTime(bucketId));
        Instant end = Instant.ofEpochMilli(bucketEndTime(bucketId) - 1);
        return String.format("Bucket %d: %s to %s", 
            bucketId, 
            FORMATTER.format(start), 
            FORMATTER.format(end)
        );
    }
    
    /**
     * Get bucket size in milliseconds (exposed for configuration).
     */
    public static long getBucketSizeMs() {
        return BUCKET_SIZE_MS;
    }
    
    public static long getEpochStart() {
        return EPOCH_START;
    }
}
