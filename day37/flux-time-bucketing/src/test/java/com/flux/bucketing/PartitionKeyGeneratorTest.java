package com.flux.bucketing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PartitionKeyGeneratorTest {
    
    @Test
    void testCalculateBucket_FirstBucket() {
        // 2024-01-05 (5 days after epoch)
        long timestamp = 1704067200000L + (5L * 24 * 60 * 60 * 1000);
        int bucket = PartitionKeyGenerator.calculateBucket(timestamp);
        assertEquals(0, bucket, "Should be in first bucket");
    }
    
    @Test
    void testCalculateBucket_SecondBucket() {
        // 2024-01-15 (14 days after epoch)
        long timestamp = 1704067200000L + (14L * 24 * 60 * 60 * 1000);
        int bucket = PartitionKeyGenerator.calculateBucket(timestamp);
        assertEquals(1, bucket, "Should be in second bucket");
    }
    
    @Test
    void testCalculateBucket_BoundaryCase() {
        // Exactly 10 days after epoch (bucket boundary)
        long timestamp = 1704067200000L + (10L * 24 * 60 * 60 * 1000);
        int bucket = PartitionKeyGenerator.calculateBucket(timestamp);
        assertEquals(1, bucket, "Boundary should belong to next bucket");
    }
    
    @Test
    void testBucketStartTime() {
        int bucket = 0;
        long startTime = PartitionKeyGenerator.bucketStartTime(bucket);
        assertEquals(1704067200000L, startTime);
    }
    
    @Test
    void testBucketEndTime() {
        int bucket = 0;
        long endTime = PartitionKeyGenerator.bucketEndTime(bucket);
        long expected = 1704067200000L + (10L * 24 * 60 * 60 * 1000);
        assertEquals(expected, endTime);
    }
    
    @Test
    void testBucketsForRange_SingleBucket() {
        long start = 1704067200000L;
        long end = start + (5L * 24 * 60 * 60 * 1000); // 5 days later
        List<Integer> buckets = PartitionKeyGenerator.bucketsForRange(start, end);
        assertEquals(List.of(0), buckets);
    }
    
    @Test
    void testBucketsForRange_MultipleBuckets() {
        long start = 1704067200000L;
        long end = start + (25L * 24 * 60 * 60 * 1000); // 25 days (3 buckets)
        List<Integer> buckets = PartitionKeyGenerator.bucketsForRange(start, end);
        assertEquals(List.of(0, 1, 2), buckets);
    }
    
    @Test
    void testCalculateBucket_BeforeEpoch_ThrowsException() {
        long timestamp = 1704067200000L - 1000; // Before epoch
        assertThrows(IllegalArgumentException.class, 
            () -> PartitionKeyGenerator.calculateBucket(timestamp));
    }
    
    @Test
    void testBucketsForRange_InvalidRange_ThrowsException() {
        long start = 1704067200000L;
        long end = start - 1000; // End before start
        assertThrows(IllegalArgumentException.class,
            () -> PartitionKeyGenerator.bucketsForRange(start, end));
    }
    
    @Test
    void testDeterminism() {
        long timestamp = System.currentTimeMillis();
        int bucket1 = PartitionKeyGenerator.calculateBucket(timestamp);
        int bucket2 = PartitionKeyGenerator.calculateBucket(timestamp);
        assertEquals(bucket1, bucket2, "Same timestamp must produce same bucket");
    }
}
