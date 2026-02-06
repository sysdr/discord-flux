package com.flux.persistence;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ReplicaNode {
    private final int nodeId;
    private final ConcurrentHashMap<Long, Message> storage = new ConcurrentHashMap<>();
    private final Random latencyJitter = new Random();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean isPartitioned = false;
    private volatile int baseLatencyMs = 5;
    
    public ReplicaNode(int nodeId) {
        this.nodeId = nodeId;
    }
    
    public CompletableFuture<WriteResult> write(Message message) {
        long startTime = System.currentTimeMillis();
        
        return CompletableFuture.supplyAsync(() -> {
            if (isPartitioned) {
                return new WriteResult(nodeId, false, 0, "Node partitioned");
            }
            
            simulateNetworkLatency();
            storage.put(message.id(), message);
            
            long latency = System.currentTimeMillis() - startTime;
            return new WriteResult(nodeId, true, latency);
        }, executor);
    }
    
    public CompletableFuture<ReadResult> read(long messageId) {
        long startTime = System.currentTimeMillis();
        
        return CompletableFuture.supplyAsync(() -> {
            if (isPartitioned) {
                return new ReadResult(nodeId, Optional.empty(), 0, false);
            }
            
            simulateNetworkLatency();
            Message msg = storage.get(messageId);
            
            long latency = System.currentTimeMillis() - startTime;
            return new ReadResult(nodeId, msg, latency);
        }, executor);
    }
    
    private void simulateNetworkLatency() {
        try {
            int jitter = latencyJitter.nextInt(10); // 0-10ms jitter
            Thread.sleep(baseLatencyMs + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void setPartitioned(boolean partitioned) {
        this.isPartitioned = partitioned;
    }
    
    public void setBaseLatency(int latencyMs) {
        this.baseLatencyMs = latencyMs;
    }
    
    public int getStorageSize() {
        return storage.size();
    }
    
    public int getNodeId() {
        return nodeId;
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
