package com.flux;

import com.flux.simulator.MessageSimulator;
import com.flux.simulator.SimulationResult;

public class DemoRunner {
    public static void main(String[] args) {
        MessageSimulator sim = new MessageSimulator();
        
        System.out.println("Simulating 1000 users over 90 days...\n");
        
        System.out.println("⏳ Running NAIVE simulation (single partition per user)...");
        SimulationResult naive = sim.simulateWorkload(1000, 90, false);
        System.out.println(naive);
        
        System.out.println("⏳ Running BUCKETED simulation (10-day windows)...");
        SimulationResult bucketed = sim.simulateWorkload(1000, 90, true);
        System.out.println(bucketed);
        
        System.out.println("📊 ANALYSIS:");
        System.out.printf("  Partition reduction: %.1fx fewer max size\n", 
            (double) naive.maxPartitionSize() / bucketed.maxPartitionSize());
        System.out.printf("  Distribution improvement: %d partitions vs %d\n",
            bucketed.numPartitions(), naive.numPartitions());
    }
}
