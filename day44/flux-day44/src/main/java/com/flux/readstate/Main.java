package com.flux.readstate;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Flux Day 44 — Read State / Ack Tracker
 *
 * Startup sequence:
 *   1. Initialize SnowflakeIdGenerator (node 1)
 *   2. Start AckTracker (in-memory store)
 *   3. Start CassandraSink (simulated Cassandra with latency)
 *   4. Start DirtyQueueFlusher (Virtual Thread, 5-second interval)
 *   5. Start ChannelSimulator (posts messages to channels)
 *   6. Seed initial read states (simulate loading from Cassandra on startup)
 *   7. Start HTTP server + Dashboard on port 8085
 *   8. Register shutdown hook for graceful drain
 *
 * Dashboard: http://localhost:8085/dashboard
 */
public final class Main {

    private static final Logger LOG  = Logger.getLogger(Main.class.getName());
    private static final int    PORT = 8085;

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("""
            ╔══════════════════════════════════════════════════════════╗
            ║  Flux Day 44: Read States — Ack Tracker                 ║
            ║  Write Coalescing · VarHandle CAS · Dirty Queue         ║
            ╚══════════════════════════════════════════════════════════╝
            """);

        // ── 1. Core infrastructure ────────────────────────────────
        var snowflake    = new SnowflakeIdGenerator(1L);
        var ackTracker   = new AckTracker();
        var cassandraSink= new CassandraSink();
        var flusher      = new DirtyQueueFlusher(ackTracker, cassandraSink);
        var channelSim   = new ChannelSimulator(ackTracker, snowflake);

        // ── 2. Seed initial state ─────────────────────────────────
        LOG.info("Seeding initial read states (simulating Cassandra bootstrap)...");
        channelSim.seedReadStates();

        // ── 3. Start background services ─────────────────────────
        flusher.start();
        channelSim.start();

        // ── 4. Start HTTP server ──────────────────────────────────
        var httpServer = new ReadStateHttpServer(PORT, ackTracker, cassandraSink, channelSim, snowflake);
        httpServer.start();

        LOG.info("=== Server started ===");
        LOG.info("Dashboard : http://localhost:" + PORT + "/dashboard");
        LOG.info("Metrics   : http://localhost:" + PORT + "/api/metrics");
        LOG.info("States    : http://localhost:" + PORT + "/api/states");

        // ── 5. Graceful shutdown hook ─────────────────────────────
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            LOG.info("Shutdown signal received. Flushing dirty queue...");
            flusher.forceFlush();
            flusher.shutdown();
            channelSim.stop();
            httpServer.stop();
            LOG.info("Shutdown complete. Cassandra rows written: "
                + cassandraSink.getTotalRowsWritten());
        }));

        // ── 6. Check for load-test mode ──────────────────────────
        if (args.length > 0 && "loadtest".equals(args[0])) {
            int users   = args.length > 1 ? Integer.parseInt(args[1]) : 200;
            int seconds = args.length > 2 ? Integer.parseInt(args[2]) : 30;
            var loadGen = new AckLoadGenerator(ackTracker, snowflake, users, seconds);
            var result  = loadGen.run();
            result.print();
            System.exit(0);
        }

        // ── 7. Keep main thread alive ─────────────────────────────
        Thread.currentThread().join();
    }
}
