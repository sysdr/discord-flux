package com.flux.simulator;

/**
 * Results of a simulation run.
 */
public record SimulationResult(
    long totalMessages,
    int numPartitions,
    long maxPartitionSize,
    double avgPartitionSize,
    long executionTimeMs,
    boolean wasBucketed
) {
    @Override
    public String toString() {
        return String.format("""
            Simulation Results (%s):
            - Total Messages: %,d
            - Partitions Created: %,d
            - Max Partition Size: %,d messages
            - Avg Partition Size: %.2f messages
            - Execution Time: %d ms
            """,
            wasBucketed ? "BUCKETED" : "NAIVE",
            totalMessages, numPartitions, maxPartitionSize, avgPartitionSize, executionTimeMs
        );
    }
}
