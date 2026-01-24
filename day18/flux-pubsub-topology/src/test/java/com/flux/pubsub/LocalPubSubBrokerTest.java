package com.flux.pubsub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LocalPubSubBrokerTest {
    private LocalPubSubBroker broker;
    
    @BeforeEach
    void setUp() {
        broker = new LocalPubSubBroker();
    }
    
    @Test
    void testBasicPubSub() {
        var subscriber = new GatewaySubscriber("test_user", 1024, null);
        broker.subscribe("guild:123", subscriber);
        
        byte[] message = "Hello World".getBytes(StandardCharsets.UTF_8);
        broker.publish("guild:123", message);
        
        assertEquals(1, subscriber.receivedCount());
        assertEquals(1, broker.publicationCount());
    }
    
    @Test
    void testMultipleSubscribers() {
        var sub1 = new GatewaySubscriber("user1", 1024, null);
        var sub2 = new GatewaySubscriber("user2", 1024, null);
        
        broker.subscribe("guild:456", sub1);
        broker.subscribe("guild:456", sub2);
        
        byte[] message = "Broadcast".getBytes(StandardCharsets.UTF_8);
        broker.publish("guild:456", message);
        
        assertEquals(1, sub1.receivedCount());
        assertEquals(1, sub2.receivedCount());
        assertEquals(2, broker.totalSubscribers());
    }
    
    @Test
    void testUnsubscribe() {
        var subscriber = new GatewaySubscriber("test_user", 1024, null);
        broker.subscribe("guild:789", subscriber);
        
        assertEquals(1, broker.topicCount());
        
        broker.unsubscribe("guild:789", subscriber);
        
        assertEquals(0, broker.topicCount()); // Topic should be cleaned up
    }
    
    @Test
    void testBackpressure() {
        // Small buffer to trigger drops
        var subscriber = new GatewaySubscriber("slow_user", 4, null);
        broker.subscribe("guild:999", subscriber);
        
        // Fill buffer
        for (int i = 0; i < 10; i++) {
            byte[] message = ("Message " + i).getBytes(StandardCharsets.UTF_8);
            broker.publish("guild:999", message);
        }
        
        // Should have drops due to small buffer
        assertTrue(subscriber.droppedCount() > 0);
        assertTrue(broker.dropCount() > 0);
    }
}
