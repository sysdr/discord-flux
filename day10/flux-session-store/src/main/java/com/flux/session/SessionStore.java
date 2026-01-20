package com.flux.session;

import java.util.Collection;
import java.util.Optional;

public interface SessionStore {
    void addSession(Session session);
    Optional<Session> getSession(long sessionId);
    void updateSession(Session session);
    void removeSession(long sessionId);
    Collection<Session> getAllSessions();
    int size();
    void clear();
}
