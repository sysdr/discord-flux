package com.flux.pubsub;

import com.flux.pubsub.core.EventType;
import com.flux.pubsub.core.GuildEvent;
import com.flux.pubsub.dashboard.DashboardServer;
import com.flux.pubsub.metrics.MetricsCollector;
import com.flux.pubsub.redis.StreamConsumer;
import com.flux.pubsub.redis.StreamPublisher;
import com.flux.pubsub.websocket.WebSocketSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class GatewayServer {
    private static final Logger log = LoggerFactory.getLogger(GatewayServer.class);
    private static final String REDIS_URI = "redis://localhost:6379";
    private static final String CONSUMER_GROUP = "gateway-consumers";
    private static final String CONSUMER_ID = "gateway-pod-1";

    public static void main(String[] args) throws Exception {
        log.info("🚀 Starting Flux Gateway Server (Day 16: Pub/Sub Primitives)");

        MetricsCollector metrics = new MetricsCollector();
        
        // Initialize Redis publisher and consumer
        StreamPublisher publisher = new StreamPublisher(REDIS_URI, metrics);
        StreamConsumer consumer = new StreamConsumer(REDIS_URI, CONSUMER_GROUP, CONSUMER_ID, metrics);

        // Initialize WebSocket simulator
        WebSocketSimulator wsSimulator = new WebSocketSimulator(metrics);

        // Create simulated connections for Guild 1001 (50 fast, 10 slow)
        wsSimulator.createConnections(1001, 50, false);
        wsSimulator.createConnections(1001, 10, true);

        // Subscribe to Guild 1001 events
        consumer.subscribe(1001, event -> {
            // Fan out to all WebSockets in this guild
            wsSimulator.fanOut(event);
        });

        // Start dashboard
        DashboardServer dashboard = new DashboardServer(8080, metrics);
        dashboard.start();

        log.info("✅ Gateway running:");
        log.info("   - Dashboard: http://localhost:8080/dashboard.html");
        log.info("   - WebSocket connections: {}", wsSimulator.getTotalSocketCount());
        log.info("   - Subscribed to guild:1001:events");

        // Graceful shutdown
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("🛑 Shutting down Gateway...");
            consumer.close();
            publisher.close();
            wsSimulator.stop();
            dashboard.stop();
            shutdownLatch.countDown();
        }));

        shutdownLatch.await();
        log.info("✅ Gateway stopped");
    }
}
