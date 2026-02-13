package com.flux.gateway.hashing;

import java.util.*;

/**
 * Analyzes key distribution across nodes in a consistent hash ring.
 * Used for verification and monitoring.
 */
public final class DistributionAnalyzer {
    
    /**
     * Calculate standard deviation of key distribution.
     * Lower is better (target: < 5% with 150 virtual nodes).
     */
    public static double calculateStandardDeviation(Map<String, Integer> keysPerNode) {
        if (keysPerNode.isEmpty()) return 0.0;
        
        double mean = keysPerNode.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0.0);
        
        double variance = keysPerNode.values().stream()
            .mapToDouble(count -> Math.pow(count - mean, 2))
            .average()
            .orElse(0.0);
        
        double stdDev = Math.sqrt(variance);
        return (stdDev / mean) * 100.0; // Return as percentage
    }
    
    /**
     * Calculate Gini coefficient (0 = perfect equality, 1 = perfect inequality).
     * Target: < 0.05
     */
    public static double calculateGiniCoefficient(Map<String, Integer> keysPerNode) {
        if (keysPerNode.isEmpty()) return 0.0;
        
        List<Integer> counts = new ArrayList<>(keysPerNode.values());
        counts.sort(Integer::compareTo);
        
        int n = counts.size();
        double sum = counts.stream().mapToInt(Integer::intValue).sum();
        
        if (sum == 0) return 0.0;
        
        double numerator = 0.0;
        for (int i = 0; i < n; i++) {
            numerator += (i + 1) * counts.get(i);
        }
        
        double gini = (2.0 * numerator) / (n * sum) - (n + 1.0) / n;
        return gini;
    }
    
    /**
     * Simulate key distribution by hashing N random keys.
     */
    public static Map<String, Integer> simulateDistribution(
            ConsistentHashRing ring, 
            int numKeys) {
        
        Map<String, Integer> distribution = new HashMap<>();
        Random random = new Random(42); // Fixed seed for reproducibility
        
        for (int i = 0; i < numKeys; i++) {
            String key = "session:" + random.nextLong();
            PhysicalNode node = ring.getNode(key);
            distribution.merge(node.nodeId(), 1, Integer::sum);
        }
        
        return distribution;
    }
    
    /**
     * Calculate redistribution percentage when adding a node.
     */
    public static double calculateRedistributionPercentage(
            List<String> keysBeforeAdd,
            ConsistentHashRing ringBeforeAdd,
            ConsistentHashRing ringAfterAdd) {
        
        int movedKeys = 0;
        for (String key : keysBeforeAdd) {
            PhysicalNode beforeNode = ringBeforeAdd.getNode(key);
            PhysicalNode afterNode = ringAfterAdd.getNode(key);
            if (!beforeNode.nodeId().equals(afterNode.nodeId())) {
                movedKeys++;
            }
        }
        
        return (double) movedKeys / keysBeforeAdd.size() * 100.0;
    }
}
