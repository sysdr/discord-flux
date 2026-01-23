package com.flux.pubsub.websocket;

import com.flux.pubsub.core.BoundedEventBuffer;
import com.flux.pubsub.core.GuildEvent;
import com.flux.pubsub.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebSocketSimulator {
    private static final Logger log = LoggerFactory.getLogger(WebSocketSimulator.class);
    private final ConcurrentHashMap<Long, List<SimulatedSocket>> socketsByGuild = new ConcurrentHashMap<>();
    private final MetricsCollector metrics;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public WebSocketSimulator(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    // Create simulated WebSocket connections for a guild
    public void createConnections(long guildId, int count, boolean slow) {
        List<SimulatedSocket> sockets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SimulatedSocket socket = new SimulatedSocket(
                "socket-" + guildId + "-" + i,
                slow ? 50 : 1, // Slow = 50ms processing delay
                metrics
            );
            sockets.add(socket);
            socket.start();
        }
        socketsByGuild.put(guildId, sockets);
        log.info("Created {} {} sockets for guild {}", count, slow ? "slow" : "fast", guildId);
    }

    // Fan out event to all sockets in the guild
    public void fanOut(GuildEvent event) {
        List<SimulatedSocket> sockets = socketsByGuild.get(event.guildId());
        if (sockets == null || sockets.isEmpty()) {
            return;
        }

        int fanOutCount = 0;
        long totalDropped = 0;
        
        for (SimulatedSocket socket : sockets) {
            socket.buffer.offer(event);
            fanOutCount++;
            totalDropped += socket.buffer.getDroppedCount();
        }

        metrics.recordFanOut(event.guildId(), fanOutCount);
        if (totalDropped > 0) {
            metrics.recordDropped(event.guildId(), totalDropped);
        }
    }

    public int getTotalSocketCount() {
        return socketsByGuild.values().stream()
            .mapToInt(List::size)
            .sum();
    }

    public void stop() {
        running.set(false);
        socketsByGuild.values().forEach(sockets ->
            sockets.forEach(SimulatedSocket::stop)
        );
    }

    // Simulated WebSocket connection with ring buffer
    static class SimulatedSocket {
        private final String id;
        private final BoundedEventBuffer buffer;
        private final int processingDelayMs;
        private final MetricsCollector metrics;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private Thread workerThread;

        SimulatedSocket(String id, int processingDelayMs, MetricsCollector metrics) {
            this.id = id;
            this.buffer = new BoundedEventBuffer(1000); // 1K message buffer
            this.processingDelayMs = processingDelayMs;
            this.metrics = metrics;
        }

        void start() {
            workerThread = Thread.startVirtualThread(() -> {
                while (running.get()) {
                    GuildEvent event = buffer.poll();
                    if (event == null) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }

                    // Simulate processing (serialize + TCP write)
                    try {
                        Thread.sleep(processingDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        void stop() {
            running.set(false);
            if (workerThread != null) {
                workerThread.interrupt();
            }
        }
    }
}
