package com.flux.readstate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Logger;

/**
 * Background write-coalescing engine.
 *
 * Runs as a Virtual Thread. Wakes every FLUSH_INTERVAL_MS and drains
 * the AckTracker's dirty queue in batches to Cassandra.
 *
 * The Virtual Thread choice is deliberate: the blocking sleep and the
 * blocking Cassandra write do NOT pin a platform thread. The carrier
 * thread is returned to the ForkJoinPool during both waits.
 *
 * Lifecycle: start() → [running loop] → shutdown() → forceFlush() → stop
 */
public final class DirtyQueueFlusher {

    private static final Logger LOG = Logger.getLogger(DirtyQueueFlusher.class.getName());
    private static final long   FLUSH_INTERVAL_NS = Duration.ofSeconds(5).toNanos();

    private final AckTracker    ackTracker;
    private final CassandraSink cassandraSink;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private volatile Thread     flusherThread;

    public DirtyQueueFlusher(AckTracker ackTracker, CassandraSink cassandraSink) {
        this.ackTracker   = ackTracker;
        this.cassandraSink = cassandraSink;
    }

    public void start() {
        flusherThread = Thread.ofVirtual()
            .name("dirty-queue-flusher")
            .start(this::runLoop);
        LOG.info("DirtyQueueFlusher started. Flush interval: 5s");
    }

    private void runLoop() {
        long nextWakeNs = System.nanoTime() + FLUSH_INTERVAL_NS;
        while (!shutdown.get()) {
            LockSupport.parkNanos(nextWakeNs - System.nanoTime());
            if (shutdown.get()) break;
            flush();
            nextWakeNs = System.nanoTime() + FLUSH_INTERVAL_NS;
        }
        LOG.info("DirtyQueueFlusher loop exited. Performing final flush...");
        flush(); // Drain remaining dirty entries on graceful shutdown
    }

    private void flush() {
        int totalFlushed = 0;
        List<AckKey> batch;
        while (!(batch = ackTracker.drainDirtyBatch()).isEmpty()) {
            cassandraSink.batchWrite(batch, ackTracker);
            totalFlushed += batch.size();
        }
        if (totalFlushed > 0) {
            LOG.info(String.format("Flushed %,d dirty entries to Cassandra", totalFlushed));
        }
    }

    /** Force an immediate flush — called on graceful shutdown. */
    public void forceFlush() {
        LOG.info("Force flush triggered...");
        flush();
    }

    public void shutdown() {
        shutdown.set(true);
        if (flusherThread != null) {
            LockSupport.unpark(flusherThread); // Wake it up to exit
        }
    }
}
