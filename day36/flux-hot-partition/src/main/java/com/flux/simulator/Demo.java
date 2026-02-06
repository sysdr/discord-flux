package com.flux.simulator;

import com.flux.partition.BucketStrategy;

import java.time.Duration;

/**
 * Demo program that runs partition simulation scenarios.
 */
public class Demo {
    public static void main(String[] args) {
        PartitionSimulator simulator = new PartitionSimulator(1);

        System.out.println("Scenario 1: Naive Partitioning (THE PROBLEM)");
        System.out.println("============================================");
        var result1 = simulator.simulateWrites(12345L, 100, Duration.ofSeconds(10), BucketStrategy.NAIVE);
        System.out.println(result1.summary());

        simulator.reset();

        System.out.println("\nScenario 2: Time-Bucketed Partitioning (THE SOLUTION)");
        System.out.println("====================================================");
        var result2 = simulator.simulateWrites(12345L, 100, Duration.ofSeconds(10), BucketStrategy.HOURLY);
        System.out.println(result2.summary());
    }
}
