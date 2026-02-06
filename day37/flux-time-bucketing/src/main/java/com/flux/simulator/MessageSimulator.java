package com.flux.simulator;

import com.flux.bucketing.PartitionKeyGenerator;
import com.flux.model.Message;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates realistic message write patterns with Zipfian distribution.
 * Power users generate 1000x more messages than typical users.
 */
public class MessageSimulator {
    
    private final Random random = ThreadLocalRandom.current();
    private final AtomicLong messageIdGenerator = new AtomicLong(0);
    
    /**
     * Generate messages for a single user over a time period.
     * Returns map of bucketId -> message count.
     */
    public Map<Integer, AtomicLong> generateMessagesForUser(
        UUID userId, 
        long startTimeMs, 
        long endTimeMs, 
        int messagesPerDay
    ) {
        Map<Integer, AtomicLong> bucketCounts = new ConcurrentHashMap<>();
        
        long durationMs = endTimeMs - startTimeMs;
        long totalMessages = (durationMs / (24 * 60 * 60 * 1000)) * messagesPerDay;
        
        for (int i = 0; i < totalMessages; i++) {
            // Random timestamp within range
            long timestamp = startTimeMs + (long)(random.nextDouble() * durationMs);
            int bucket = PartitionKeyGenerator.calculateBucket(timestamp);
            
            bucketCounts.computeIfAbsent(bucket, k -> new AtomicLong()).incrementAndGet();
        }
        
        return bucketCounts;
    }
    
    /**
     * Generate messages for multiple users with Zipfian distribution.
     * Returns global bucket statistics.
     */
    public SimulationResult simulateWorkload(
        int numUsers,
        int daysToSimulate,
        boolean useBucketing
    ) {
        long startTime = System.nanoTime();
        long endTimeMs = System.currentTimeMillis();
        long startTimeMs = endTimeMs - (daysToSimulate * 24L * 60 * 60 * 1000);
        
        Map<String, AtomicLong> partitionSizes = new ConcurrentHashMap<>();
        AtomicLong totalMessages = new AtomicLong(0);
        
        // Zipfian: 10% of users generate 90% of messages
        List<UserProfile> users = generateUserProfiles(numUsers);
        
        // Simulate using virtual threads
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            
            for (UserProfile user : users) {
                futures.add(executor.submit(() -> {
                    Map<Integer, AtomicLong> buckets = generateMessagesForUser(
                        user.userId, startTimeMs, endTimeMs, user.messagesPerDay
                    );
                    
                    buckets.forEach((bucket, count) -> {
                        String partitionKey;
                        if (useBucketing) {
                            partitionKey = user.userId + ":" + bucket;
                        } else {
                            // Naive: all messages in one partition
                            partitionKey = user.userId.toString();
                        }
                        
                        partitionSizes.computeIfAbsent(partitionKey, k -> new AtomicLong())
                                      .addAndGet(count.get());
                        totalMessages.addAndGet(count.get());
                    });
                }));
            }
            
            // Wait for completion
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (Exception e) {
            throw new RuntimeException("Simulation failed", e);
        }
        
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        
        return new SimulationResult(
            totalMessages.get(),
            partitionSizes.size(),
            calculateMaxPartitionSize(partitionSizes),
            calculateAvgPartitionSize(partitionSizes),
            elapsedMs,
            useBucketing
        );
    }
    
    private List<UserProfile> generateUserProfiles(int numUsers) {
        List<UserProfile> users = new ArrayList<>();
        
        // 10% power users (1000 msg/day)
        for (int i = 0; i < numUsers * 0.1; i++) {
            users.add(new UserProfile(UUID.randomUUID(), 1000));
        }
        
        // 30% active users (100 msg/day)
        for (int i = 0; i < numUsers * 0.3; i++) {
            users.add(new UserProfile(UUID.randomUUID(), 100));
        }
        
        // 60% casual users (10 msg/day)
        for (int i = 0; i < numUsers * 0.6; i++) {
            users.add(new UserProfile(UUID.randomUUID(), 10));
        }
        
        return users;
    }
    
    private long calculateMaxPartitionSize(Map<String, AtomicLong> partitions) {
        return partitions.values().stream()
                        .mapToLong(AtomicLong::get)
                        .max()
                        .orElse(0);
    }
    
    private double calculateAvgPartitionSize(Map<String, AtomicLong> partitions) {
        if (partitions.isEmpty()) return 0;
        long total = partitions.values().stream().mapToLong(AtomicLong::get).sum();
        return (double) total / partitions.size();
    }
    
    record UserProfile(UUID userId, int messagesPerDay) {}
}
