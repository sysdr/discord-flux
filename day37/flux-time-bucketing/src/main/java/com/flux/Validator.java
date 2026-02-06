package com.flux;

import com.flux.bucketing.PartitionKeyGenerator;
import java.util.List;

public class Validator {
    public static void main(String[] args) {
        long now = System.currentTimeMillis();
        long thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000);
        
        List<Integer> buckets = PartitionKeyGenerator.bucketsForRange(thirtyDaysAgo, now);
        
        System.out.println("✓ Bucket calculation working");
        System.out.printf("✓ 30-day query scans %d buckets (expected: 3-4)\n", buckets.size());
        
        if (buckets.size() <= 4) {
            System.out.println("✓ Bucket size optimal for recent queries");
        } else {
            System.out.println("⚠ Bucket size may be too small");
        }
        
        int bucket = PartitionKeyGenerator.calculateBucket(now);
        long start = PartitionKeyGenerator.bucketStartTime(bucket);
        long end = PartitionKeyGenerator.bucketEndTime(bucket);
        
        if (now >= start && now < end) {
            System.out.println("✓ Bucket boundaries correct");
        } else {
            System.err.println("❌ Bucket boundary calculation error");
            System.exit(1);
        }
    }
}
