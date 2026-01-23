package com.flux.pubsub;

import com.flux.pubsub.core.EventType;
import com.flux.pubsub.core.GuildEvent;
import com.flux.pubsub.metrics.MetricsCollector;
import com.flux.pubsub.redis.StreamPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class LoadTestPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoadTestPublisher.class);
    private static final String REDIS_URI = "redis://localhost:6379";

    public static void main(String[] args) throws Exception {
        int publishers = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int messagesPerPublisher = args.length > 1 ? Integer.parseInt(args[1]) : 100;

        log.info("🔥 Starting load test: {} publishers × {} messages = {} total",
                publishers, messagesPerPublisher, publishers * messagesPerPublisher);

        MetricsCollector metrics = new MetricsCollector();
        StreamPublisher publisher = new StreamPublisher(REDIS_URI, metrics);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(publishers);
        AtomicLong totalSent = new AtomicLong(0);

        long startTime = System.nanoTime();

        // Spawn publisher threads
        for (int i = 0; i < publishers; i++) {
            int publisherId = i;
            Thread.startVirtualThread(() -> {
                try {
                    startLatch.await(); // Wait for signal

                    for (int j = 0; j < messagesPerPublisher; j++) {
                        GuildEvent event = new GuildEvent(
                            1001,
                            EventType.MESSAGE_CREATE,
                            System.currentTimeMillis(),
                            "Load test message from publisher-" + publisherId + "-" + j
                        );
                        publisher.publish(event).join();
                        totalSent.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Publisher {} error", publisherId, e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all publishers simultaneously
        Thread.sleep(100);
        startLatch.countDown();

        // Wait for completion
        doneLatch.await();
        long endTime = System.nanoTime();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        long messagesPerSecond = (totalSent.get() * 1000) / Math.max(1, durationMs);

        log.info("✅ Load test complete:");
        log.info("   - Total messages: {}", totalSent.get());
        log.info("   - Duration: {}ms", durationMs);
        log.info("   - Throughput: {} msg/sec", messagesPerSecond);

        var snapshot = metrics.snapshot();
        log.info("   - Avg publish latency: {:.2f}ms", snapshot.avgPublishLatencyNanos() / 1_000_000.0);

        publisher.close();
    }
}
