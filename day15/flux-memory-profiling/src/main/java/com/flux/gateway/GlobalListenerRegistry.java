package com.flux.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * LEAK #4: Global registry holds listeners with closure references.
 * Listeners capture parent objects, preventing Session GC.
 */
public class GlobalListenerRegistry {
    
    // BUG: Static list never cleared
    private static final List<ListenerEntry> listeners = new ArrayList<>();
    
    record ListenerEntry(Session session, Consumer<String> listener) {}
    
    public static void register(Session session, Consumer<String> listener) {
        listeners.add(new ListenerEntry(session, listener));
    }
    
    public static void unregister(Session session) {
        // This SHOULD be called but isn't
        listeners.removeIf(entry -> entry.session().equals(session));
    }
    
    public static int getListenerCount() {
        return listeners.size();
    }
    
    public static void notifyAll(String message) {
        for (ListenerEntry entry : listeners) {
            try {
                entry.listener().accept(message);
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
