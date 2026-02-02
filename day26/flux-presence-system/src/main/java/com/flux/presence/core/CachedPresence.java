package com.flux.presence.core;

/**
 * Immutable presence entry for L1 local cache.
 * Uses Java Record for zero-allocation value semantics.
 */
public record CachedPresence(String status, long expiryTimeMillis) {
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTimeMillis;
    }
    
    public static CachedPresence online(long ttlMillis) {
        return new CachedPresence("online", System.currentTimeMillis() + ttlMillis);
    }
    
    public static CachedPresence offline() {
        return new CachedPresence("offline", System.currentTimeMillis() + 5000);
    }
}
