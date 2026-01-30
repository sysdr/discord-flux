package com.flux.gateway.reaper;

import com.flux.gateway.buffer.ConnectionState;
import com.flux.gateway.server.WebSocketServer;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public class ReaperThread implements Runnable {
    private static final long SCAN_INTERVAL_MS = 100;
    private static final long LAG_THRESHOLD = 3;  // Drop when lag_counter > 3 so demo shows drops sooner
    private static final long GRACE_PERIOD_MS = 8_000;  // 8s so drops visible within ~25–35s of demo

    private final WebSocketServer server;
    private final ExecutorService virtualExecutor;
    private volatile boolean running;
    private final AtomicLong droppedCount;

    public ReaperThread(WebSocketServer server) {
        this.server = server;
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.running = true;
        this.droppedCount = new AtomicLong(0);
    }

    @Override
    public void run() {
        System.out.println("[REAPER] Started (scan interval: " + SCAN_INTERVAL_MS + "ms, threshold: " + LAG_THRESHOLD + ")");

        while (running) {
            try {
                Thread.sleep(SCAN_INTERVAL_MS);
                scanConnections();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[REAPER] Scan error: " + e.getMessage());
            }
        }

        virtualExecutor.shutdown();
        System.out.println("[REAPER] Stopped (total dropped: " + droppedCount.get() + ")");
    }

    private void scanConnections() {
        long now = System.currentTimeMillis();

        for (var state : server.getConnections().values()) {
            if (state.isClosed()) continue;

            if (now - state.getCreatedAt() < GRACE_PERIOD_MS) {
                continue;
            }

            long lagCounter = state.getLagCounter();

            if (lagCounter > LAG_THRESHOLD) {
                virtualExecutor.submit(() -> {
                    server.forceClose(state);
                    droppedCount.incrementAndGet();
                });
            }
        }
    }

    public long getDroppedCount() {
        return droppedCount.get();
    }

    public void shutdown() {
        running = false;
    }
}
