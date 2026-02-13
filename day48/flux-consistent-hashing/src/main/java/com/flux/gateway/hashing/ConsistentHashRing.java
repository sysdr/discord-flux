package com.flux.gateway.hashing;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A consistent hash ring implementation using virtual nodes.
 * 
 * Thread-Safety: All methods are thread-safe. Ring updates use copy-on-write
 * semantics with VarHandle for lock-free reads.
 * 
 * Performance: 
 * - Lookup: O(log V) where V = virtualNodesPerNode * physicalNodeCount
 * - Add/Remove Node: O(V log V) due to TreeMap rebuild
 * - Zero allocations in the lookup hot path
 */
public final class ConsistentHashRing {
    
    private static final int DEFAULT_VIRTUAL_NODES = 150;
    private volatile TreeMap<Long, PhysicalNode> ring;
    private volatile Map<String, PhysicalNode> physicalNodes;
    private final int virtualNodesPerNode;
    
    private static final VarHandle RING;
    private static final VarHandle PHYSICAL_NODES;
    
    static {
        try {
            var lookup = MethodHandles.lookup();
            RING = lookup.findVarHandle(ConsistentHashRing.class, "ring", TreeMap.class);
            PHYSICAL_NODES = lookup.findVarHandle(ConsistentHashRing.class, "physicalNodes", Map.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    public ConsistentHashRing() {
        this(DEFAULT_VIRTUAL_NODES);
    }
    
    public ConsistentHashRing(int virtualNodesPerNode) {
        this.virtualNodesPerNode = virtualNodesPerNode;
        this.ring = new TreeMap<>();
        this.physicalNodes = new ConcurrentHashMap<>();
    }
    
    /**
     * Batch initialization of the ring with multiple nodes.
     * Prefer this over multiple addNode() calls for initial setup.
     */
    public ConsistentHashRing(List<PhysicalNode> initialNodes, int virtualNodesPerNode) {
        this.virtualNodesPerNode = virtualNodesPerNode;
        TreeMap<Long, PhysicalNode> tempRing = new TreeMap<>();
        Map<String, PhysicalNode> tempPhysicalNodes = new ConcurrentHashMap<>();
        
        for (PhysicalNode node : initialNodes) {
            addVirtualNodes(tempRing, node);
            tempPhysicalNodes.put(node.nodeId(), node);
        }
        
        this.ring = tempRing;
        this.physicalNodes = tempPhysicalNodes;
    }
    
    /**
     * Get the physical node responsible for the given key.
     * This is the HOT PATH - executed on every packet/request.
     * 
     * Zero allocations: works directly with byte arrays.
     */
    public PhysicalNode getNode(byte[] keyBytes) {
        long hash = MurmurHash3.hash64(keyBytes);
        return getNodeForHash(hash);
    }
    
    public PhysicalNode getNode(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        return getNode(keyBytes);
    }
    
    private PhysicalNode getNodeForHash(long hash) {
        TreeMap<Long, PhysicalNode> currentRing = (TreeMap<Long, PhysicalNode>) RING.getAcquire(this);
        
        if (currentRing.isEmpty()) {
            throw new IllegalStateException("Ring is empty - no nodes available");
        }
        
        // Find first virtual node >= hash
        Map.Entry<Long, PhysicalNode> entry = currentRing.ceilingEntry(hash);
        
        if (entry == null) {
            // Wrap around: hash is beyond the last virtual node
            entry = currentRing.firstEntry();
        }
        
        return entry.getValue();
    }
    
    /**
     * Add a physical node to the ring.
     * Creates virtualNodesPerNode virtual nodes for uniform distribution.
     */
    public void addNode(PhysicalNode node) {
        // Copy-on-write: create new ring
        TreeMap<Long, PhysicalNode> newRing = new TreeMap<>(ring);
        Map<String, PhysicalNode> newPhysicalNodes = new ConcurrentHashMap<>(physicalNodes);
        
        addVirtualNodes(newRing, node);
        newPhysicalNodes.put(node.nodeId(), node);
        
        // Atomic swap with release semantics for safe publication
        PHYSICAL_NODES.setRelease(this, newPhysicalNodes);
        RING.setRelease(this, newRing);
    }
    
    /**
     * Remove a physical node from the ring.
     * Returns the set of key hashes that need to be migrated.
     */
    public Set<Long> removeNode(String nodeId) {
        Map<String, PhysicalNode> currentPhysicalNodes = 
            (Map<String, PhysicalNode>) PHYSICAL_NODES.getAcquire(this);
        
        PhysicalNode removedNode = currentPhysicalNodes.get(nodeId);
        if (removedNode == null) {
            return Collections.emptySet();
        }
        
        TreeMap<Long, PhysicalNode> newRing = new TreeMap<>(ring);
        Map<String, PhysicalNode> newPhysicalNodes = new ConcurrentHashMap<>(physicalNodes);
        
        // Find and remove all virtual nodes for this physical node
        Set<Long> affectedHashes = new HashSet<>();
        newRing.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(removedNode)) {
                affectedHashes.add(entry.getKey());
                return true;
            }
            return false;
        });
        
        newPhysicalNodes.remove(nodeId);
        
        PHYSICAL_NODES.setRelease(this, newPhysicalNodes);
        RING.setRelease(this, newRing);
        
        return affectedHashes;
    }
    
    private void addVirtualNodes(TreeMap<Long, PhysicalNode> targetRing, PhysicalNode node) {
        int effectiveVirtualNodes = virtualNodesPerNode * node.weight();
        
        for (int vnode = 0; vnode < effectiveVirtualNodes; vnode++) {
            String vnodeId = node.nodeId() + ":" + vnode;
            long hash = MurmurHash3.hash64(vnodeId);
            
            // Handle hash collisions (extremely rare with 2^64 space)
            int collisionIndex = 0;
            while (targetRing.containsKey(hash)) {
                vnodeId = node.nodeId() + ":" + vnode + ":" + collisionIndex++;
                hash = MurmurHash3.hash64(vnodeId);
            }
            
            targetRing.put(hash, node);
        }
    }
    
    /**
     * Get current ring state for visualization.
     */
    public RingSnapshot getSnapshot() {
        TreeMap<Long, PhysicalNode> currentRing = (TreeMap<Long, PhysicalNode>) RING.getAcquire(this);
        Map<String, PhysicalNode> currentPhysicalNodes = 
            (Map<String, PhysicalNode>) PHYSICAL_NODES.getAcquire(this);
        
        return new RingSnapshot(
            new TreeMap<>(currentRing),
            new HashMap<>(currentPhysicalNodes)
        );
    }
    
    public int getPhysicalNodeCount() {
        return ((Map<String, PhysicalNode>) PHYSICAL_NODES.getAcquire(this)).size();
    }
    
    public int getVirtualNodeCount() {
        return ((TreeMap<Long, PhysicalNode>) RING.getAcquire(this)).size();
    }
    
    /**
     * Immutable snapshot of the ring state at a point in time.
     */
    public record RingSnapshot(
        TreeMap<Long, PhysicalNode> virtualNodes,
        Map<String, PhysicalNode> physicalNodes
    ) {}
}
