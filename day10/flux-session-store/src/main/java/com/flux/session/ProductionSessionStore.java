package com.flux.session;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Production-grade session store using ConcurrentHashMap with lock striping.
 * 
 * Features:
 * - Lock-free reads, striped writes (16 segments)
 * - Passive cleanup on access (lazy eviction)
 * - Active cleanup via background task
 * - Metrics tracking
 */
public class ProductionSessionStore implements SessionStore {
    private static final Logger logger = Logger.getLogger(ProductionSessionStore.class.getName());

    private final ConcurrentHashMap<Long, Session> sessions;
    private final long idleTimeoutSeconds;
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicInteger cleanupCount = new AtomicInteger(0);

    public ProductionSessionStore(int initialCapacity, long idleTimeoutSeconds) {
        this.sessions = new ConcurrentHashMap<>(initialCapacity, 0.75f, 16);
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("session-cleanup-", 0).factory()
        );
        startCleanupTask();
    }

    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                int removed = cleanupStale();
                if (removed > 0) {
                    logger.info(String.format("Cleanup removed %d stale sessions (total cleanups: %d)",
                        removed, cleanupCount.incrementAndGet()));
                }
            } catch (Exception e) {
                logger.severe("Cleanup task failed: " + e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public void addSession(Session session) {
        sessions.put(session.sessionId(), session);
    }

    @Override
    public Optional<Session> getSession(long sessionId) {
        // Passive cleanup: remove if stale on access
        Session session = sessions.computeIfPresent(sessionId, (key, value) -> {
            if (value.isStale(idleTimeoutSeconds)) {
                logger.fine("Passive cleanup: removing stale session " + sessionId);
                return null; // Remove from map
            }
            return value;
        });
        return Optional.ofNullable(session);
    }

    @Override
    public void updateSession(Session session) {
        sessions.put(session.sessionId(), session);
    }

    @Override
    public void removeSession(long sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public Collection<Session> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }

    @Override
    public int size() {
        return sessions.size();
    }

    @Override
    public void clear() {
        sessions.clear();
    }

    /**
     * Active cleanup: scan all sessions and remove stale ones.
     * Uses Iterator.remove() for safe concurrent modification.
     */
    public int cleanupStale() {
        Instant cutoff = Instant.now().minusSeconds(idleTimeoutSeconds);
        int removed = 0;

        Iterator<Map.Entry<Long, Session>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Session> entry = iterator.next();
            if (entry.getValue().lastActivity().isBefore(cutoff)) {
                iterator.remove();
                removed++;
            }
        }

        return removed;
    }

    public SessionMetrics getMetrics() {
        long heapUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        int total = sessions.size();
        int active = 0, idle = 0, zombie = 0;

        for (Session session : sessions.values()) {
            switch (session.state()) {
                case ACTIVE -> active++;
                case IDLE -> idle++;
                case ZOMBIE -> zombie++;
            }
        }

        return new SessionMetrics(
            total,
            active,
            idle,
            zombie,
            heapUsed / (1024 * 1024), // Convert to MB
            cleanupCount.get()
        );
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
