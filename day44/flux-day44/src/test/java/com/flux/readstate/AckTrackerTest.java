package com.flux.readstate;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AckTracker — Write Coalescing & Dirty Queue")
class AckTrackerTest {

    private AckTracker tracker;
    private SnowflakeIdGenerator snowflake;

    @BeforeEach
    void setUp() {
        tracker   = new AckTracker();
        snowflake = new SnowflakeIdGenerator(1L);
    }

    @Test
    @DisplayName("First ack creates new entry and marks CREATED")
    void firstAckCreatesEntry() {
        long msgId  = snowflake.nextId();
        var  result = tracker.ack(new AckCommand(1L, 1L, msgId, 0));
        assertEquals(AckResult.CREATED, result);
        assertEquals(1, tracker.states().size());
        assertFalse(tracker.dirtyQueue().isEmpty());
    }

    @Test
    @DisplayName("Stale ack is dropped, dirty queue not polluted")
    void staleAckIsDropped() {
        long first  = snowflake.nextId();
        long second = snowflake.nextId();
        tracker.ack(new AckCommand(1L, 1L, second, 0));
        tracker.drainDirtyBatch(); // Clear dirty queue

        var result  = tracker.ack(new AckCommand(1L, 1L, first, 0));
        assertEquals(AckResult.STALE, result);
        assertTrue(tracker.dirtyQueue().isEmpty(), "Stale ack must not re-dirty the queue");
    }

    @Test
    @DisplayName("Write coalescing: 1000 acks on same key = 1 dirty entry")
    void writeCoalescingCollapses1000Acks() {
        for (int i = 0; i < 1000; i++) {
            tracker.ack(new AckCommand(1L, 1L, snowflake.nextId(), 0));
        }
        assertEquals(1, tracker.dirtyQueue().size(),
            "Dirty queue must have exactly 1 entry regardless of ack volume");
    }

    @Test
    @DisplayName("drainDirtyBatch marks entries as FLUSHING")
    void drainSetsFlushing() {
        tracker.ack(new AckCommand(1L, 1L, snowflake.nextId(), 0));
        var batch = tracker.drainDirtyBatch();
        assertFalse(batch.isEmpty());
        var entry = tracker.states().get(new AckKey(1L, 1L));
        assertNotNull(entry);
        assertEquals(ReadStateEntry.STATE_FLUSHING, entry.getState());
    }

    @Test
    @DisplayName("onFlushSuccess marks entries CLEAN and increments counter")
    void flushSuccessMarksClean() {
        tracker.ack(new AckCommand(1L, 1L, snowflake.nextId(), 0));
        var batch   = tracker.drainDirtyBatch();
        tracker.onFlushSuccess(batch);
        var entry   = tracker.states().get(new AckKey(1L, 1L));
        assertNotNull(entry);
        assertEquals(ReadStateEntry.STATE_CLEAN, entry.getState());
        assertEquals(1, tracker.getMetrics().cassandraWrites());
    }

    @Test
    @DisplayName("onFlushFailure re-queues entries as DIRTY")
    void flushFailureRequeuesToDirty() {
        tracker.ack(new AckCommand(1L, 1L, snowflake.nextId(), 0));
        var batch = tracker.drainDirtyBatch();
        tracker.onFlushFailure(batch);
        var entry = tracker.states().get(new AckKey(1L, 1L));
        assertNotNull(entry);
        assertEquals(ReadStateEntry.STATE_DIRTY, entry.getState());
        assertFalse(tracker.dirtyQueue().isEmpty(), "Failed flush must re-enqueue dirty key");
    }

    @Test
    @DisplayName("Concurrent multi-user acks maintain per-user monotonic order")
    void concurrentMultiUserAcks() throws InterruptedException {
        int users    = 50;
        int acksEach = 100;
        var latch    = new CountDownLatch(users);
        var maxIds   = new ConcurrentHashMap<Long, AtomicLong>();

        for (int u = 1; u <= users; u++) {
            final long userId = u;
            maxIds.put(userId, new AtomicLong(0));
            Thread.ofVirtual().start(() -> {
                try {
                    for (int i = 0; i < acksEach; i++) {
                        long msgId = snowflake.nextId();
                        maxIds.get(userId).updateAndGet(cur -> Math.max(cur, msgId));
                        tracker.ack(new AckCommand(userId, 1L, msgId, 0));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        // Each user's final state must be the maximum ID they ever acked
        for (int u = 1; u <= users; u++) {
            var entry  = tracker.states().get(new AckKey(u, 1L));
            assertNotNull(entry, "Entry must exist for user " + u);
            assertEquals(maxIds.get((long) u).get(), entry.getLastReadMessageId(),
                "User " + u + " final state must be the highest acked ID");
        }
    }

    @Test
    @DisplayName("Coalescing ratio exceeds 10:1 under burst ack load")
    void coalescingRatioUnderLoad() {
        for (int u = 1; u <= 20; u++) {
            for (int i = 0; i < 100; i++) {
                tracker.ack(new AckCommand(u, 1L, snowflake.nextId(), 0));
            }
        }
        var batch = tracker.drainDirtyBatch();
        tracker.onFlushSuccess(batch);
        var metrics = tracker.getMetrics();
        double ratio = metrics.coalescingRatio();
        assertTrue(ratio >= 10.0,
            "Coalescing ratio must be >= 10:1 under burst, was: " + ratio);
    }
}
