package com.flux.loadbalancer;

import com.flux.loadbalancer.models.GatewayNode;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistentHash {
    private static final int VIRTUAL_NODES = 150; // For better distribution
    private final SortedMap<Integer, GatewayNode> ring = new TreeMap<>();
    
    public void addNode(GatewayNode node) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            var hash = hash(node.nodeId() + "#" + i);
            ring.put(hash, node);
        }
    }
    
    public void removeNode(GatewayNode node) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            var hash = hash(node.nodeId() + "#" + i);
            ring.remove(hash);
        }
    }
    
    public GatewayNode getNode(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        
        var hash = hash(key);
        var tailMap = ring.tailMap(hash);
        
        var resultHash = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        return ring.get(resultHash);
    }
    
    public void rebuild(List<GatewayNode> nodes) {
        ring.clear();
        nodes.forEach(this::addNode);
    }
    
    public int getNodeCount() {
        return ring.isEmpty() ? 0 : (int) ring.values().stream().distinct().count();
    }
    
    private int hash(String key) {
        // Simple hash function (in production, use MurmurHash3)
        return Math.abs(key.hashCode());
    }
}
