package com.flux.rebalancing;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe consistent hash ring implementation using virtual nodes.
 * Uses ConcurrentSkipListMap for lock-free reads during lookups.
 */
public class ConsistentHashRing {
    
    private final ConcurrentSkipListMap<Integer, GatewayNode> ring;
    private final Map<String, GatewayNode> physicalNodes;
    private final int virtualNodesPerNode;
    private final ReadWriteLock ringLock;
    
    public ConsistentHashRing(int virtualNodesPerNode) {
        this.ring = new ConcurrentSkipListMap<>();
        this.physicalNodes = new HashMap<>();
        this.virtualNodesPerNode = virtualNodesPerNode;
        this.ringLock = new ReentrantReadWriteLock();
    }
    
    /**
     * MurmurHash3 32-bit implementation for fast, uniform hashing.
     * Chosen over Object.hashCode() to avoid clustering on sequential IDs.
     */
    private int murmurHash3(String key) {
        byte[] data = key.getBytes(StandardCharsets.UTF_8);
        int h1 = 0x12345678; // seed
        
        int len = data.length;
        int roundedEnd = (len & 0xfffffffc); // Round down to 4 byte block
        
        for (int i = 0; i < roundedEnd; i += 4) {
            int k1 = (data[i] & 0xff) | 
                     ((data[i + 1] & 0xff) << 8) | 
                     ((data[i + 2] & 0xff) << 16) |
                     (data[i + 3] << 24);
            k1 *= 0xcc9e2d51;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= 0x1b873593;
            
            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }
        
        // Tail
        int k1 = 0;
        switch (len & 0x03) {
            case 3:
                k1 = (data[roundedEnd + 2] & 0xff) << 16;
            case 2:
                k1 |= (data[roundedEnd + 1] & 0xff) << 8;
            case 1:
                k1 |= (data[roundedEnd] & 0xff);
                k1 *= 0xcc9e2d51;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= 0x1b873593;
                h1 ^= k1;
        }
        
        // Finalization
        h1 ^= len;
        h1 ^= (h1 >>> 16);
        h1 *= 0x85ebca6b;
        h1 ^= (h1 >>> 13);
        h1 *= 0xc2b2ae35;
        h1 ^= (h1 >>> 16);
        
        return h1;
    }
    
    public void addNode(GatewayNode node) {
        ringLock.writeLock().lock();
        try {
            physicalNodes.put(node.nodeId(), node);
            
            // Add virtual nodes to spread load
            for (int i = 0; i < virtualNodesPerNode; i++) {
                String virtualKey = node.getVirtualNodeKey(i);
                int hash = murmurHash3(virtualKey);
                ring.put(hash, node);
            }
        } finally {
            ringLock.writeLock().unlock();
        }
    }
    
    public void removeNode(String nodeId) {
        ringLock.writeLock().lock();
        try {
            GatewayNode node = physicalNodes.remove(nodeId);
            if (node == null) return;
            
            // Remove all virtual nodes
            for (int i = 0; i < virtualNodesPerNode; i++) {
                String virtualKey = node.getVirtualNodeKey(i);
                int hash = murmurHash3(virtualKey);
                ring.remove(hash);
            }
        } finally {
            ringLock.writeLock().unlock();
        }
    }
    
    /**
     * Lock-free lookup using ConcurrentSkipListMap.
     * O(log n) where n = total virtual nodes.
     */
    public GatewayNode getNodeForConnection(String connectionId) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Ring is empty - no nodes available");
        }
        
        int hash = murmurHash3(connectionId);
        
        // Find first node clockwise from hash position
        Map.Entry<Integer, GatewayNode> entry = ring.ceilingEntry(hash);
        
        // Wrap around if no ceiling found
        if (entry == null) {
            entry = ring.firstEntry();
        }
        
        return entry.getValue();
    }
    
    public int getPhysicalNodeCount() {
        ringLock.readLock().lock();
        try {
            return physicalNodes.size();
        } finally {
            ringLock.readLock().unlock();
        }
    }
    
    public Collection<GatewayNode> getPhysicalNodes() {
        ringLock.readLock().lock();
        try {
            return new ArrayList<>(physicalNodes.values());
        } finally {
            ringLock.readLock().unlock();
        }
    }
    
    public int getRingSize() {
        return ring.size();
    }
    
    /**
     * Calculate load distribution variance.
     * Coefficient of Variation (CV) < 0.05 indicates good balance.
     */
    public double calculateLoadVariance(Map<String, GatewayNode> connectionMap) {
        var nodeLoadCounts = new HashMap<String, Integer>();
        
        for (var node : physicalNodes.values()) {
            nodeLoadCounts.put(node.nodeId(), 0);
        }
        
        for (var node : connectionMap.values()) {
            nodeLoadCounts.merge(node.nodeId(), 1, Integer::sum);
        }
        
        if (nodeLoadCounts.isEmpty()) return 0.0;
        
        double mean = connectionMap.size() / (double) physicalNodes.size();
        if (mean == 0.0) return 0.0; // avoid NaN
        double variance = nodeLoadCounts.values().stream()
            .mapToDouble(count -> Math.pow(count - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance) / mean; // Coefficient of Variation
    }
}
