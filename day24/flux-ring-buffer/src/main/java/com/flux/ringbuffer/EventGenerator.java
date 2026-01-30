package com.flux.ringbuffer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates a high-throughput event stream (like Redis Pub/Sub).
 */
public class EventGenerator implements Runnable {
    private final Gateway gateway;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong eventCounter = new AtomicLong(0);
    private final int targetEventsPerSecond;
    
    public EventGenerator(Gateway gateway, int targetEventsPerSecond) {
        this.gateway = gateway;
        this.targetEventsPerSecond = targetEventsPerSecond;
    }
    
    public void start() {
        running.set(true);
        Thread.ofVirtual().name("event-generator").start(this);
    }
    
    public void stop() {
        running.set(false);
    }
    
    @Override
    public void run() {
        long intervalNanos = 1_000_000_000L / targetEventsPerSecond;
        long nextEventTime = System.nanoTime();
        
        while (running.get()) {
            long now = System.nanoTime();
            if (now >= nextEventTime) {
                long eventId = eventCounter.incrementAndGet();
                GuildEvent event = GuildEvent.create(
                    eventId,
                    "guild-1",
                    String.format("Event %d: Message content", eventId)
                );
                
                gateway.broadcast(event);
                nextEventTime += intervalNanos;
            } else {
                // Busy wait for precision (in production use better scheduling)
                Thread.onSpinWait();
            }
        }
    }
    
    public long getEventsGenerated() {
        return eventCounter.get();
    }
}
