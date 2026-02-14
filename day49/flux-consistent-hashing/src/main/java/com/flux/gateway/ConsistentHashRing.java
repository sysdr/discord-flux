package com.flux.gateway;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.StampedLock;

/**
 * Production-grade Consistent Hash Ring implementation.
 * Uses TreeMap for O(log n) binary search and StampedLock for lock-free reads.
 * 
 * @param <T> The type of nodes in the ring (e.g., GatewayNode)
 */
public class ConsistentHashRing<T> {
    
    private final TreeMap<Long, T> ring;
    private final StampedLock lock;
    private final int virtualNodesPerNode;
    private final Map<T, Set<Long>> nodeToHashes;
    
    public ConsistentHashRing(int virtualNodesPerNode) {
        this.ring = new TreeMap<>();
        this.lock = new StampedLock();
        this.virtualNodesPerNode = virtualNodesPerNode;
        this.nodeToHashes = new HashMap<>();
    }
    
    /**
     * Add a node to the ring with virtual replicas.
     * This is a write operation that acquires a write lock.
     */
    public void addNode(T node) {
        long stamp = lock.writeLock();
        try {
            Set<Long> hashes = new HashSet<>();
            for (int i = 0; i < virtualNodesPerNode; i++) {
                String virtualKey = node.toString() + "#" + i;
                long hash = murmurHash3(virtualKey.getBytes(StandardCharsets.UTF_8));
                ring.put(hash, node);
                hashes.add(hash);
            }
            nodeToHashes.put(node, hashes);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    /**
     * Remove a node from the ring.
     * This is a write operation that acquires a write lock.
     */
    public void removeNode(T node) {
        long stamp = lock.writeLock();
        try {
            Set<Long> hashes = nodeToHashes.remove(node);
            if (hashes != null) {
                for (Long hash : hashes) {
                    ring.remove(hash);
                }
            }
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    /**
     * Get the node responsible for the given key.
     * Uses optimistic locking for lock-free reads in the common case.
     */
    public T get(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        return get(keyBytes);
    }
    
    /**
     * Get the node responsible for the given key bytes.
     * Zero-copy version that operates directly on byte array.
     */
    public T get(byte[] keyBytes) {
        if (ring.isEmpty()) {
            return null;
        }
        
        long hash = murmurHash3(keyBytes);
        
        // Optimistic read - no locking in the common case
        long stamp = lock.tryOptimisticRead();
        Map.Entry<Long, T> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        T result = entry != null ? entry.getValue() : null;
        
        // Validate that no write occurred
        if (!lock.validate(stamp)) {
            // Rare path: a write happened, acquire read lock
            stamp = lock.readLock();
            try {
                entry = ring.ceilingEntry(hash);
                if (entry == null) {
                    entry = ring.firstEntry();
                }
                result = entry != null ? entry.getValue() : null;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        
        return result;
    }
    
    /**
     * Get distribution statistics for all nodes.
     */
    public Map<T, Integer> getDistribution(List<String> keys) {
        Map<T, Integer> distribution = new HashMap<>();
        for (String key : keys) {
            T node = get(key);
            if (node != null) {
                distribution.merge(node, 1, Integer::sum);
            }
        }
        return distribution;
    }
    
    /**
     * Get all nodes in the ring.
     */
    public Set<T> getNodes() {
        long stamp = lock.readLock();
        try {
            return new HashSet<>(nodeToHashes.keySet());
        } finally {
            lock.unlockRead(stamp);
        }
    }
    
    /**
     * Get the number of virtual nodes in the ring.
     */
    public int size() {
        long stamp = lock.tryOptimisticRead();
        int size = ring.size();
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                size = ring.size();
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return size;
    }
    
    /**
     * Murmur3-inspired hash function.
     * Operates on byte[] to avoid String allocation overhead.
     */
    private long murmurHash3(byte[] data) {
        long h = 0xDEADBEEF_CAFEBABEL;
        
        int len = data.length;
        int i = 0;
        
        // Process 8 bytes at a time
        while (i + 8 <= len) {
            long k = getLong(data, i);
            k *= 0xC6A4A793_5BD1E995L;
            k ^= k >>> 47;
            k *= 0x9E3779B9_7F4A7C15L;
            
            h ^= k;
            h *= 0x9E3779B9_7F4A7C15L;
            
            i += 8;
        }
        
        // Process remaining bytes
        if (i < len) {
            long k = 0;
            for (int j = 0; i < len; i++, j += 8) {
                k ^= ((long) (data[i] & 0xFF)) << j;
            }
            k *= 0xC6A4A793_5BD1E995L;
            k ^= k >>> 47;
            h ^= k;
            h *= 0x9E3779B9_7F4A7C15L;
        }
        
        h ^= h >>> 47;
        h *= 0xC6A4A793_5BD1E995L;
        h ^= h >>> 47;
        
        return h;
    }
    
    /**
     * Extract 8 bytes as a long (little-endian).
     */
    private long getLong(byte[] data, int offset) {
        long result = 0;
        for (int i = 0; i < 8 && offset + i < data.length; i++) {
            result |= ((long) (data[offset + i] & 0xFF)) << (i * 8);
        }
        return result;
    }
}
