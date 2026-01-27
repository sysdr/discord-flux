package com.flux.subscriber;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionManagerTest {
    @Test
    void testAddUserToGuild() {
        var mockSubscriber = new MockRedisStreamSubscriber();
        var manager = new SubscriptionManager(mockSubscriber);

        boolean isFirst = manager.addUserToGuild("guild1", "conn1");
        assertTrue(isFirst, "First user should trigger subscription");

        boolean isSecond = manager.addUserToGuild("guild1", "conn2");
        assertFalse(isSecond, "Second user should not trigger subscription");

        assertEquals(1, manager.getTotalSubscriptions());
    }

    @Test
    void testRemoveUserFromGuild() {
        var mockSubscriber = new MockRedisStreamSubscriber();
        var manager = new SubscriptionManager(mockSubscriber);

        manager.addUserToGuild("guild1", "conn1");
        manager.addUserToGuild("guild1", "conn2");

        manager.removeUserFromGuild("guild1", "conn1");
        assertEquals(1, manager.getTotalSubscriptions(), "Should still be subscribed with one user");

        manager.removeUserFromGuild("guild1", "conn2");
        // Lazy unsubscription means it won't drop immediately
        assertEquals(1, manager.getTotalSubscriptions(), "Lazy unsubscription delays removal");
    }

    // Mock subscriber for testing
    static class MockRedisStreamSubscriber extends RedisStreamSubscriber {
        public MockRedisStreamSubscriber() {
            super("redis://localhost:6379", null);
        }

        @Override
        public void subscribe(String guildId) {
            System.out.println("Mock: Subscribed to " + guildId);
        }

        @Override
        public void unsubscribe(String guildId) {
            System.out.println("Mock: Unsubscribed from " + guildId);
        }
    }
}
