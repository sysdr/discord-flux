package com.flux.subscriber;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class SubscriptionManager {
    private final ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<String, Boolean>> 
        guildSubscriptions = new ConcurrentHashMap<>();
    
    private final RedisStreamSubscriber streamSubscriber;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong totalSubscriptions = new AtomicLong(0);
    private final AtomicLong subscriptionChurnCount = new AtomicLong(0);
    
    // Metrics
    private final ConcurrentHashMap<String, AtomicLong> messagesDeliveredPerGuild 
        = new ConcurrentHashMap<>();
    private final AtomicLong unroutableMessages = new AtomicLong(0);

    public SubscriptionManager(RedisStreamSubscriber streamSubscriber) {
        this.streamSubscriber = streamSubscriber;
        this.scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());
    }

    public boolean addUserToGuild(String guildId, String connectionId) {
        var connections = guildSubscriptions.computeIfAbsent(
            guildId, 
            k -> ConcurrentHashMap.newKeySet()
        );
        
        boolean isFirstUser = connections.isEmpty();
        connections.add(connectionId);
        
        if (isFirstUser) {
            System.out.printf("[SubscriptionManager] First local user for guild %s - subscribing%n", 
                guildId);
            streamSubscriber.subscribe(guildId);
            totalSubscriptions.incrementAndGet();
            subscriptionChurnCount.incrementAndGet();
        }
        
        return isFirstUser;
    }

    public void removeUserFromGuild(String guildId, String connectionId) {
        var connections = guildSubscriptions.get(guildId);
        if (connections == null) return;
        
        connections.remove(connectionId);
        
        if (connections.isEmpty()) {
            System.out.printf("[SubscriptionManager] Last user left guild %s - scheduling unsubscribe%n", 
                guildId);
            scheduleUnsubscription(guildId, Duration.ofSeconds(30)); // 30 sec for demo
        }
    }

    private void scheduleUnsubscription(String guildId, Duration delay) {
        scheduler.schedule(() -> {
            var connections = guildSubscriptions.get(guildId);
            if (connections != null && connections.isEmpty()) {
                System.out.printf("[SubscriptionManager] Lazy unsubscribe for guild %s%n", guildId);
                streamSubscriber.unsubscribe(guildId);
                guildSubscriptions.remove(guildId);
                totalSubscriptions.decrementAndGet();
                subscriptionChurnCount.incrementAndGet();
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void dispatchMessage(String guildId, Message msg) {
        var connections = guildSubscriptions.get(guildId);
        
        if (connections == null || connections.isEmpty()) {
            unroutableMessages.incrementAndGet();
            return;
        }
        
        // Track delivery
        messagesDeliveredPerGuild.computeIfAbsent(guildId, k -> new AtomicLong())
            .incrementAndGet();
        
        // Dispatch to all local connections
        connections.forEach(connId -> {
            // In real implementation, this would write to actual WebSocket connection
            System.out.printf("[Dispatch] Guild %s → Connection %s: %s%n", 
                guildId, connId, msg.content());
        });
    }

    public long getTotalSubscriptions() {
        return totalSubscriptions.get();
    }

    public long getSubscriptionChurnCount() {
        return subscriptionChurnCount.get();
    }

    public long getUnroutableMessages() {
        return unroutableMessages.get();
    }

    public long getTotalMessagesDelivered() {
        return messagesDeliveredPerGuild.values().stream()
            .mapToLong(AtomicLong::get)
            .sum();
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
