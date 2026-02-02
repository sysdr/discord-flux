package com.flux.presence.cache;

import com.flux.presence.core.CachedPresence;
import com.flux.presence.core.PresenceStatus;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L1 local cache for presence data.
 * Reduces Redis queries by 95%+ for frequently accessed users.
 */
public class LocalPresenceCache {
    
    private final ConcurrentHashMap<Long, CachedPresence> cache = new ConcurrentHashMap<>();
    private final long defaultTtlMillis;
    
    // Metrics
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    
    public LocalPresenceCache(long ttlMillis) {
        this.defaultTtlMillis = ttlMillis;
    }
    
    /**
     * Get presence from cache. Returns null if expired or not present.
     */
    public PresenceStatus get(long userId) {
        CachedPresence cached = cache.get(userId);
        
        if (cached != null && !cached.isExpired()) {
            hits.incrementAndGet();
            return PresenceStatus.fromString(cached.status());
        }
        
        // Cache miss or expired entry
        if (cached != null) {
            cache.remove(userId); // Clean up expired entry
        }
        misses.incrementAndGet();
        return null;
    }
    
    /**
     * Store presence in cache with default TTL.
     */
    public void put(long userId, PresenceStatus status) {
        long expiry = System.currentTimeMillis() + defaultTtlMillis;
        cache.put(userId, new CachedPresence(status.getValue(), expiry));
    }
    
    /**
     * Invalidate a user's cached presence.
     */
    public void invalidate(long userId) {
        cache.remove(userId);
    }
    
    /**
     * Clear all cached entries.
     */
    public void clear() {
        cache.clear();
    }
    
    /**
     * Get cache hit rate as percentage.
     */
    public double getHitRate() {
        long totalQueries = hits.get() + misses.get();
        if (totalQueries == 0) return 0.0;
        return (double) hits.get() / totalQueries * 100.0;
    }
    
    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        return new CacheStats(hits.get(), misses.get(), cache.size());
    }
    
    public record CacheStats(long hits, long misses, int size) {
        public double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total * 100.0;
        }
    }
}
