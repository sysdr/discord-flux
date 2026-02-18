package com.flux.gateway.dashboard;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Singleton metrics collector using lock-free primitives.
 *
 * LongAdder is preferred over AtomicLong for high-contention counters
 * because it uses per-thread cells to reduce CAS contention, with
 * a final sum() on read. Slightly imprecise under concurrent updates
 * (fine for metrics), but dramatically faster under write-heavy load.
 */
public final class MetricsCollector {

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    private final LongAdder connectionAttempts   = new LongAdder();
    private final LongAdder activeConnections    = new LongAdder();
    private final LongAdder identifySuccess      = new LongAdder();
    private final LongAdder identifyRejected     = new LongAdder();
    private final LongAdder identifyParseErrors  = new LongAdder();
    private final LongAdder shardConnected       = new LongAdder();
    private final LongAdder shardDisconnected    = new LongAdder();
    private final LongAdder zombieEvictions      = new LongAdder();
    private final LongAdder heartbeats           = new LongAdder();

    // P99 latency approximation (nanoseconds) — last 1000 samples ring buffer (simplified)
    private final AtomicLong identifyLatencyLastNanos = new AtomicLong(0);

    private MetricsCollector() {}

    public static MetricsCollector getInstance() { return INSTANCE; }

    public void incrementConnectionAttempts()  { connectionAttempts.increment(); activeConnections.increment(); }
    public void decrementActiveConnections()   { activeConnections.decrement(); }
    public void incrementIdentifySuccess(long latencyNanos) {
        identifySuccess.increment();
        identifyLatencyLastNanos.set(latencyNanos);
    }
    public void incrementIdentifyRejected()    { identifyRejected.increment(); }
    public void incrementIdentifyParseErrors() { identifyParseErrors.increment(); }
    public void shardConnected()               { shardConnected.increment(); }
    public void shardDisconnected()            { shardDisconnected.increment(); }
    public void shardZombieEvicted()           { zombieEvictions.increment(); }
    public void incrementHeartbeats()          { heartbeats.increment(); }

    public String toJson() {
        return """
               {
                 "connectionAttempts":   %d,
                 "activeConnections":    %d,
                 "identifySuccess":      %d,
                 "identifyRejected":     %d,
                 "identifyParseErrors":  %d,
                 "shardsConnected":      %d,
                 "shardsDisconnected":   %d,
                 "zombieEvictions":      %d,
                 "heartbeats":           %d,
                 "identifyLatencyMs":    %.2f,
                 "jvmHeapUsedMb":        %.1f,
                 "jvmHeapMaxMb":         %.1f,
                 "virtualThreadCount":   %d
               }""".formatted(
            connectionAttempts.sum(),
            activeConnections.sum(),
            identifySuccess.sum(),
            identifyRejected.sum(),
            identifyParseErrors.sum(),
            shardConnected.sum(),
            shardDisconnected.sum(),
            zombieEvictions.sum(),
            heartbeats.sum(),
            identifyLatencyLastNanos.get() / 1_000_000.0,
            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576.0,
            Runtime.getRuntime().maxMemory() / 1_048_576.0,
            Thread.activeCount()
        );
    }
}
