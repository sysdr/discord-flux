package com.flux.readstate;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReadStateEntry — VarHandle CAS Correctness")
class ReadStateEntryTest {

    private ReadStateEntry entry;

    @BeforeEach
    void setUp() {
        entry = new ReadStateEntry(1L, 1L, 100L);
    }

    @Test
    @DisplayName("tryAdvance advances past current value")
    void advancesForward() {
        assertTrue(entry.tryAdvance(200L));
        assertEquals(200L, entry.getLastReadMessageId());
        assertEquals(ReadStateEntry.STATE_DIRTY, entry.getState());
    }

    @Test
    @DisplayName("tryAdvance rejects stale (lower) message ID")
    void rejectsStale() {
        assertFalse(entry.tryAdvance(99L));
        assertEquals(100L, entry.getLastReadMessageId());
        assertEquals(ReadStateEntry.STATE_CLEAN, entry.getState());
    }

    @Test
    @DisplayName("tryAdvance rejects equal message ID")
    void rejectsEqual() {
        assertFalse(entry.tryAdvance(100L));
        assertEquals(100L, entry.getLastReadMessageId());
    }

    @Test
    @DisplayName("Concurrent advance — only highest ID wins, no data loss")
    void concurrentAdvanceMustBeMonotonic() throws InterruptedException {
        int threadCount = 50;
        long maxId      = 100L + threadCount;
        var latch       = new CountDownLatch(threadCount);
        var winCount    = new AtomicInteger(0);

        for (int i = 1; i <= threadCount; i++) {
            final long msgId = 100L + i;
            Thread.ofVirtual().start(() -> {
                try {
                    if (entry.tryAdvance(msgId)) winCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);

        // The final state must equal the maximum ID attempted
        assertEquals(maxId, entry.getLastReadMessageId(),
            "Final state must be the highest ID, not an intermediate value");
        assertTrue(winCount.get() >= 1, "At least one advance must succeed");
        assertEquals(ReadStateEntry.STATE_DIRTY, entry.getState());
    }

    @Test
    @DisplayName("tryBeginFlush: only one thread owns flush in race")
    void flushRaceCondition() throws InterruptedException {
        entry.tryAdvance(200L); // Make dirty
        int threadCount = 20;
        var latch       = new CountDownLatch(threadCount);
        var winners     = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    if (entry.tryBeginFlush()) winners.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(3, TimeUnit.SECONDS);
        assertEquals(1, winners.get(), "Exactly one thread must win the flush CAS");
        assertEquals(ReadStateEntry.STATE_FLUSHING, entry.getState());
    }

    @Test
    @DisplayName("markClean after flush restores CLEAN state")
    void flushCycle() {
        entry.tryAdvance(200L);
        assertTrue(entry.tryBeginFlush());
        entry.markClean();
        assertEquals(ReadStateEntry.STATE_CLEAN, entry.getState());
    }

    @Test
    @DisplayName("markDirtyAfterFailure restores DIRTY from FLUSHING")
    void flushRetryOnFailure() {
        entry.tryAdvance(200L);
        entry.tryBeginFlush();
        entry.markDirtyAfterFailure();
        assertEquals(ReadStateEntry.STATE_DIRTY, entry.getState());
    }

    @Test
    @DisplayName("Mention count operations are atomic")
    void mentionCounting() throws InterruptedException {
        int threads = 20;
        var latch   = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            Thread.ofVirtual().start(() -> {
                try { entry.addMentions(1); }
                finally { latch.countDown(); }
            });
        }
        latch.await(3, TimeUnit.SECONDS);
        assertEquals(threads, entry.getMentionCount());
    }
}
