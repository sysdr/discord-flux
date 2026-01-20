package com.flux.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class SessionStoreTest {

    private Session createTestSession(long id) {
        return new Session(
            id,
            1000 + id,
            new InetSocketAddress("127.0.0.1", 5000),
            Instant.now(),
            Instant.now(),
            SessionState.ACTIVE
        );
    }

    @Test
    void testNaiveSessionStore_BasicOperations() {
        NaiveSessionStore store = new NaiveSessionStore();
        Session session = createTestSession(1);
        
        store.addSession(session);
        assertEquals(1, store.size());
        
        assertTrue(store.getSession(1).isPresent());
        assertFalse(store.getSession(999).isPresent());
        
        store.removeSession(1);
        assertEquals(0, store.size());
    }

    @Test
    void testProductionSessionStore_BasicOperations() {
        ProductionSessionStore store = new ProductionSessionStore(100, 300);
        Session session = createTestSession(1);
        
        store.addSession(session);
        assertEquals(1, store.size());
        
        assertTrue(store.getSession(1).isPresent());
        assertFalse(store.getSession(999).isPresent());
        
        store.removeSession(1);
        assertEquals(0, store.size());
        
        store.shutdown();
    }

    @Test
    void testProductionSessionStore_PassiveCleanup() {
        ProductionSessionStore store = new ProductionSessionStore(100, 1); // 1 sec timeout
        
        // Create stale session (activity 2 seconds ago)
        Session staleSession = new Session(
            1, 1000,
            new InetSocketAddress("127.0.0.1", 5000),
            Instant.now().minusSeconds(10),
            Instant.now().minusSeconds(2),
            SessionState.IDLE
        );
        
        store.addSession(staleSession);
        assertEquals(1, store.size());
        
        // Access should trigger passive cleanup
        assertFalse(store.getSession(1).isPresent());
        assertEquals(0, store.size());
        
        store.shutdown();
    }

    @Test
    void testProductionSessionStore_ActiveCleanup() {
        ProductionSessionStore store = new ProductionSessionStore(100, 1);
        
        // Add fresh session
        store.addSession(createTestSession(1));
        
        // Add stale session
        Session staleSession = new Session(
            2, 1001,
            new InetSocketAddress("127.0.0.1", 5000),
            Instant.now().minusSeconds(10),
            Instant.now().minusSeconds(5),
            SessionState.IDLE
        );
        store.addSession(staleSession);
        
        assertEquals(2, store.size());
        
        // Manual cleanup should remove stale
        int removed = store.cleanupStale();
        assertEquals(1, removed);
        assertEquals(1, store.size());
        assertTrue(store.getSession(1).isPresent());
        assertFalse(store.getSession(2).isPresent());
        
        store.shutdown();
    }

    @Test
    void testConcurrentAccess_NaiveVsProduction() throws InterruptedException {
        int threadCount = 100;
        int operationsPerThread = 1000;
        
        // Test Naive
        NaiveSessionStore naiveStore = new NaiveSessionStore();
        long naiveTime = benchmarkStore(naiveStore, threadCount, operationsPerThread);
        
        // Test Production
        ProductionSessionStore prodStore = new ProductionSessionStore(100000, 300);
        long prodTime = benchmarkStore(prodStore, threadCount, operationsPerThread);
        
        prodStore.shutdown();
        
        System.out.println("Naive Store: " + naiveTime + "ms");
        System.out.println("Production Store: " + prodTime + "ms");
        System.out.println("Speedup: " + (double)naiveTime / prodTime + "x");
        
        // Production should be significantly faster
        assertTrue(prodTime < naiveTime, 
            "Production store should be faster than naive implementation");
    }

    private long benchmarkStore(SessionStore store, int threadCount, int opsPerThread) 
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < opsPerThread; j++) {
                        long sessionId = threadId * opsPerThread + j;
                        Session session = createTestSession(sessionId);
                        store.addSession(session);
                        store.getSession(sessionId);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long end = System.currentTimeMillis();
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        return end - start;
    }

    @Test
    void testSessionMetrics() {
        ProductionSessionStore store = new ProductionSessionStore(100, 300);
        
        store.addSession(createTestSession(1));
        store.addSession(createTestSession(2).updateState(SessionState.IDLE));
        store.addSession(createTestSession(3).updateState(SessionState.ZOMBIE));
        
        SessionMetrics metrics = store.getMetrics();
        assertEquals(3, metrics.totalSessions());
        assertEquals(1, metrics.activeSessions());
        assertEquals(1, metrics.idleSessions());
        assertEquals(1, metrics.zombieSessions());
        assertTrue(metrics.heapUsedMB() > 0);
        
        store.shutdown();
    }
}
