package com.flux.gateway;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Immutable work unit passed from I/O thread to worker pool.
 * Uses Java 21 Record for zero-boilerplate data carrier.
 */
public record Task(
    long connectionId,
    ByteBuffer payload,          // Sliced view of frame data
    long enqueuedAtNanos,        // For latency tracking
    InetSocketAddress clientAddr
) {
    /**
     * Extract the payload as a String (for JSON parsing).
     * NOTE: This creates a heap allocation - in production, use zero-copy parsers.
     */
    public String payloadAsString() {
        byte[] bytes = new byte[payload.remaining()];
        payload.get(bytes);
        payload.rewind(); // Reset for potential re-reads
        return new String(bytes);
    }

    /**
     * Calculate how long this task has been queued.
     */
    public long queueLatencyMicros() {
        return (System.nanoTime() - enqueuedAtNanos) / 1_000;
    }
}
