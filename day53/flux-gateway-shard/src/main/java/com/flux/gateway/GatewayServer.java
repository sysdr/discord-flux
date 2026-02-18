package com.flux.gateway;

import com.flux.gateway.connection.GatewayConnection;
import com.flux.gateway.dashboard.DashboardServer;
import com.flux.gateway.dashboard.MetricsCollector;
import com.flux.gateway.shard.ShardRegistry;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Flux Gateway Server — Day 53
 *
 * Architecture:
 *   - ONE platform thread: NIO accept loop (non-blocking accept on ServerSocketChannel)
 *   - N virtual threads: one per accepted connection (blocking I/O via Loom)
 *   - ShardRegistry: atomic-per-key ConcurrentHashMap for shard ownership
 *   - DashboardServer: lightweight HTTP server for real-time shard grid
 */
public final class GatewayServer {

    public static final int GATEWAY_PORT   = 8888;
    public static final int DASHBOARD_PORT = 8080;

    public static void main(String[] args) throws Exception {
        // Enable virtual thread pinning diagnostics
        System.setProperty("jdk.tracePinnedThreads", "short");

        var registry = new ShardRegistry();
        var metrics  = MetricsCollector.getInstance();

        // Start dashboard on a virtual thread
        var dashboard = new DashboardServer(DASHBOARD_PORT, registry, metrics);
        Thread.ofVirtual().name("dashboard-server").start(dashboard);

        // NIO ServerSocketChannel — blocking accept on a dedicated platform thread
        try (var serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(GATEWAY_PORT), 1024 /* backlog */);
            serverChannel.configureBlocking(true);

            System.out.printf("""
                ╔══════════════════════════════════════════╗
                ║    Flux Gateway — Day 53: Shard Logic    ║
                ╠══════════════════════════════════════════╣
                ║  WebSocket : ws://localhost:%-14d║
                ║  Dashboard : http://localhost:%-13d║
                ╚══════════════════════════════════════════╝
                %n""", GATEWAY_PORT, DASHBOARD_PORT);

            var connectionId = new AtomicLong(0);

            // Virtual thread executor — each connection runs in its own virtual thread
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                while (!Thread.currentThread().isInterrupted()) {
                    SocketChannel clientChannel = serverChannel.accept();
                    clientChannel.configureBlocking(true);

                    long connId = connectionId.incrementAndGet();
                    metrics.incrementConnectionAttempts();

                    executor.submit(new GatewayConnection(connId, clientChannel, registry, metrics));
                }
            }
        }
    }
}
