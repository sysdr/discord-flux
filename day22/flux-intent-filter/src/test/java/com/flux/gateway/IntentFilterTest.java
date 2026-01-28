package com.flux.gateway;

import com.flux.gateway.connection.GatewayConnection;
import com.flux.gateway.intent.GatewayIntent;
import com.flux.gateway.model.GatewayEvent;
import com.flux.gateway.router.IntentAwareRouter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IntentFilterTest {
    private IntentAwareRouter router;

    @BeforeEach
    void setUp() {
        router = new IntentAwareRouter();
    }

    @Test
    @DisplayName("Connection should receive events matching its intents")
    void testIntentMatching() {
        long intents = GatewayIntent.GUILD_MESSAGES.mask;
        var conn = new GatewayConnection("user-1", intents, false);
        router.registerConnection(conn);
        
        var messageEvent = GatewayEvent.messageCreate("guild-1", "Hello");
        router.dispatch(messageEvent, Set.of("user-1"));
        
        assertEquals(1, conn.getEventsSent());
        assertEquals(0, conn.getEventsFiltered());
    }

    @Test
    @DisplayName("Connection should filter events not matching its intents")
    void testIntentFiltering() {
        long intents = GatewayIntent.GUILD_MESSAGES.mask;
        var conn = new GatewayConnection("user-1", intents, false);
        router.registerConnection(conn);
        
        var presenceEvent = GatewayEvent.presenceUpdate("user-2", "online");
        router.dispatch(presenceEvent, Set.of("user-1"));
        
        assertEquals(0, conn.getEventsSent());
        assertEquals(1, conn.getEventsFiltered());
    }

    @Test
    @DisplayName("Unverified connection cannot request privileged intents")
    void testPrivilegedIntentValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            new GatewayConnection("user-1", GatewayIntent.MESSAGE_CONTENT.mask, false);
        });
    }

    @Test
    @DisplayName("Verified connection can request privileged intents")
    void testVerifiedPrivilegedIntents() {
        assertDoesNotThrow(() -> {
            new GatewayConnection("user-1", GatewayIntent.MESSAGE_CONTENT.mask, true);
        });
    }

    @Test
    @DisplayName("Combined intents should match any matching event")
    void testCombinedIntents() {
        long intents = GatewayIntent.combine(
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.GUILD_PRESENCES
        );
        var conn = new GatewayConnection("user-1", intents, true);
        router.registerConnection(conn);
        
        var messageEvent = GatewayEvent.messageCreate("guild-1", "Hello");
        var presenceEvent = GatewayEvent.presenceUpdate("user-2", "online");
        var voiceEvent = GatewayEvent.voiceStateUpdate("guild-1", "voice-1");
        
        router.dispatch(messageEvent, Set.of("user-1"));
        router.dispatch(presenceEvent, Set.of("user-1"));
        router.dispatch(voiceEvent, Set.of("user-1"));
        
        assertEquals(2, conn.getEventsSent());
        assertEquals(1, conn.getEventsFiltered());
    }

    @Test
    @DisplayName("Broadcast should respect all connection intents")
    void testBroadcast() {
        var conn1 = new GatewayConnection("user-1", GatewayIntent.GUILD_MESSAGES.mask, false);
        var conn2 = new GatewayConnection("user-2", GatewayIntent.GUILD_PRESENCES.mask, true);
        
        router.registerConnection(conn1);
        router.registerConnection(conn2);
        
        var messageEvent = GatewayEvent.messageCreate("guild-1", "Hello");
        router.broadcast(messageEvent);
        
        assertEquals(1, conn1.getEventsSent());
        assertEquals(0, conn2.getEventsSent());
        assertEquals(0, conn1.getEventsFiltered());
        assertEquals(1, conn2.getEventsFiltered());
    }

    @Test
    @DisplayName("Intent update should be thread-safe")
    void testConcurrentIntentUpdate() throws InterruptedException {
        var conn = new GatewayConnection("user-1", GatewayIntent.GUILD_MESSAGES.mask, true);
        
        Thread t1 = Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 1000; i++) {
                conn.updateIntents(GatewayIntent.GUILD_MESSAGES.mask);
            }
        });
        
        Thread t2 = Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 1000; i++) {
                conn.updateIntents(GatewayIntent.GUILD_PRESENCES.mask);
            }
        });
        
        t1.join();
        t2.join();
        
        // Should not crash or corrupt state
        long finalIntents = conn.getIntents();
        assertTrue(finalIntents == GatewayIntent.GUILD_MESSAGES.mask || 
                   finalIntents == GatewayIntent.GUILD_PRESENCES.mask);
    }

    @Test
    @DisplayName("Router metrics should track filter rate accurately")
    void testMetricsTracking() {
        var conn = new GatewayConnection("user-1", GatewayIntent.GUILD_MESSAGES.mask, false);
        router.registerConnection(conn);
        
        // Send mixed events
        for (int i = 0; i < 100; i++) {
            if (i % 2 == 0) {
                router.dispatch(GatewayEvent.messageCreate("g1", "msg"), Set.of("user-1"));
            } else {
                router.dispatch(GatewayEvent.presenceUpdate("u1", "online"), Set.of("user-1"));
            }
        }
        
        var metrics = router.getMetrics();
        assertEquals(100, metrics.totalEventsProcessed());
        assertEquals(50, metrics.totalEventsSent());
        assertEquals(50, metrics.totalEventsFiltered());
        assertEquals(50.0, metrics.filterRate(), 0.1);
    }

    @Test
    @DisplayName("Bandwidth savings should accumulate correctly")
    void testBandwidthSavings() {
        var conn = new GatewayConnection("user-1", GatewayIntent.GUILD_MESSAGES.mask, false);
        router.registerConnection(conn);
        
        // Send events that should be filtered
        for (int i = 0; i < 10; i++) {
            var event = GatewayEvent.presenceUpdate("u" + i, "online");
            router.dispatch(event, Set.of("user-1"));
        }
        
        var metrics = router.getMetrics();
        assertEquals(10 * 280, metrics.bandwidthSaved()); // 280 bytes per presence event
    }

    @Test
    @DisplayName("Intent check should have minimal latency")
    void testIntentCheckPerformance() {
        var conn = new GatewayConnection("user-1", GatewayIntent.GUILD_MESSAGES.mask, false);
        router.registerConnection(conn);
        
        // Warm up
        for (int i = 0; i < 1000; i++) {
            var event = GatewayEvent.messageCreate("g1", "msg");
            router.dispatch(event, Set.of("user-1"));
        }
        
        // Measure
        long start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            var event = GatewayEvent.messageCreate("g1", "msg");
            router.dispatch(event, Set.of("user-1"));
        }
        long elapsed = System.nanoTime() - start;
        long avgLatency = elapsed / 100000;
        
        System.out.println("Average intent check latency: " + avgLatency + " ns");
        assertTrue(avgLatency < 10_000, "Intent check should be < 10μs (bitwise check is cheap)");
    }
}
