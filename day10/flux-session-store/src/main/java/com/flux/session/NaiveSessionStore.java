package com.flux.session;

import java.util.*;

/**
 * NAIVE IMPLEMENTATION - DO NOT USE IN PRODUCTION
 * 
 * Problems:
 * 1. Single global lock (synchronized methods) = all threads serialize
 * 2. No cleanup mechanism = memory leak
 * 3. HashMap resizing under lock = long pause times
 */
public class NaiveSessionStore implements SessionStore {
    private final Map<Long, Session> sessions = new HashMap<>();

    @Override
    public synchronized void addSession(Session session) {
        sessions.put(session.sessionId(), session);
    }

    @Override
    public synchronized Optional<Session> getSession(long sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public synchronized void updateSession(Session session) {
        sessions.put(session.sessionId(), session);
    }

    @Override
    public synchronized void removeSession(long sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public synchronized Collection<Session> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }

    @Override
    public synchronized int size() {
        return sessions.size();
    }

    @Override
    public synchronized void clear() {
        sessions.clear();
    }
}
