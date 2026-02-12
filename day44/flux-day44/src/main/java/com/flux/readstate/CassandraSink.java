package com.flux.readstate;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Simulated Cassandra write sink.
 *
 * Models the real behavior:
 *   - Batch writes to simulate UNLOGGED BATCH in Cassandra
 *   - P50 latency ~8ms, P99 ~45ms
 *   - Occasional simulated timeouts to exercise retry logic
 *   - Write rate tracking for dashboard
 *
 * In production, replace batchWrite() with:
 *   session.execute(BatchStatement.builder(DefaultBatchType.UNLOGGED)
 *       .addStatements(stmts).build());
 */
public final class CassandraSink {

    private static final Logger LOG = Logger.getLogger(CassandraSink.class.getName());

    private final AtomicBoolean injectTimeout = new AtomicBoolean(false);
    private final AtomicLong    totalRowsWritten = new AtomicLong(0);

    /**
     * Simulate a Cassandra UNLOGGED BATCH write.
     * Blocks the calling Virtual Thread for P50 latency.
     */
    public void batchWrite(List<AckKey> keys, AckTracker tracker) {
        if (keys.isEmpty()) return;

        var rng = ThreadLocalRandom.current();

        // Simulate network + Cassandra coordinator latency
        int latencyMs = injectTimeout.get() ? 800 + rng.nextInt(400)  // Timeout scenario
                                            : 6  + rng.nextInt(20);   // Normal P50-P95

        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            tracker.onFlushFailure(keys);
            return;
        }

        // Simulate 1% timeout failure rate
        if (!injectTimeout.get() && rng.nextDouble() < 0.01) {
            LOG.warning("Simulated Cassandra timeout for batch of " + keys.size());
            tracker.onFlushFailure(keys);
            return;
        }

        // Success — accumulate all row writes
        totalRowsWritten.addAndGet(keys.size());

        // Log the CQL that *would* execute in production
        if (LOG.isLoggable(java.util.logging.Level.FINE)) {
            keys.forEach(k -> LOG.fine(
                "EXECUTE: UPDATE read_states SET last_read_message_id = ? WHERE user_id="
                + k.userId() + " AND channel_id=" + k.channelId()
            ));
        }

        tracker.onFlushSuccess(keys);
    }

    public void setInjectTimeout(boolean inject) { injectTimeout.set(inject); }
    public boolean isInjectingTimeout()          { return injectTimeout.get(); }
    public long getTotalRowsWritten()            { return totalRowsWritten.get(); }
}
