package com.flux.pubsub;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory pub/sub broker optimized for guild-centric message routing.
 * Uses CopyOnWriteArraySet for lock-free subscriber iteration.
 */
public class LocalPubSubBroker {
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<Subscriber>> topics = new ConcurrentHashMap<>();
    private final AtomicLong totalPublications = new AtomicLong(0);
    private final AtomicLong totalDrops = new AtomicLong(0);
    
    /**
     * Publish a message to all subscribers of a topic.
     * Non-blocking: if subscriber buffer is full, message is dropped.
     */
    public void publish(String topic, byte[] message) {
        totalPublications.incrementAndGet();
        var subscribers = topics.get(topic);
        
        if (subscribers != null) {
            for (var sub : subscribers) {
                boolean accepted = sub.onMessage(message);
                if (!accepted) {
                    totalDrops.incrementAndGet();
                }
            }
        }
    }
    
    /**
     * Subscribe to a topic. Creates topic if it doesn't exist.
     */
    public void subscribe(String topic, Subscriber subscriber) {
        topics.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>())
              .add(subscriber);
    }
    
    /**
     * Unsubscribe from a topic. Cleans up empty topics.
     */
    public void unsubscribe(String topic, Subscriber subscriber) {
        var subs = topics.get(topic);
        if (subs != null) {
            subs.remove(subscriber);
            if (subs.isEmpty()) {
                topics.remove(topic); // Prevent memory leak
            }
        }
    }
    
    /**
     * Get all subscribers for a topic.
     */
    public Set<Subscriber> getSubscribers(String topic) {
        return topics.getOrDefault(topic, new CopyOnWriteArraySet<>());
    }
    
    public int topicCount() {
        return topics.size();
    }
    
    public int totalSubscribers() {
        return topics.values().stream()
                     .mapToInt(Set::size)
                     .sum();
    }
    
    public long publicationCount() {
        return totalPublications.get();
    }
    
    public long dropCount() {
        return totalDrops.get();
    }
    
    public void reset() {
        topics.clear();
        totalPublications.set(0);
        totalDrops.set(0);
    }
}
