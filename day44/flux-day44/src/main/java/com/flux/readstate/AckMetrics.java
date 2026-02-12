package com.flux.readstate;

/**
 * Aggregated counters for the AckTracker.
 * Exposed over HTTP for dashboard polling.
 */
public record AckMetrics(
    long   totalAcks,
    long   staleAcks,
    long   newEntries,
    int    dirtyQueueDepth,
    int    totalEntries,
    long   cassandraWrites,
    long   cassandraBatchCount,
    double coalescingRatio,
    double ackRatePerSec,
    double cassandraWriteRatePerSec
) {}
