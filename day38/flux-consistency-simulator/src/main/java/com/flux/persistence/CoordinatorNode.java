package com.flux.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CoordinatorNode {
    private final List<ReplicaNode> replicas;
    private final int replicationFactor;
    
    public CoordinatorNode(List<ReplicaNode> replicas) {
        this.replicas = new ArrayList<>(replicas);
        this.replicationFactor = replicas.size();
    }
    
    public WriteResult write(Message message, ConsistencyLevel level) {
        long startTime = System.currentTimeMillis();
        int requiredAcks = level.getRequiredAcks(replicationFactor);
        
        // Send write to all replicas in parallel
        List<CompletableFuture<WriteResult>> futures = replicas.stream()
            .map(replica -> replica.write(message))
            .collect(Collectors.toList());
        
        try {
            if (level == ConsistencyLevel.ONE) {
                // Return as soon as ANY replica acknowledges
                WriteResult result = (WriteResult) CompletableFuture.anyOf(
                    futures.toArray(new CompletableFuture[0])
                ).get(100, TimeUnit.MILLISECONDS);
                
                long totalLatency = System.currentTimeMillis() - startTime;
                return new WriteResult(result.replicaId(), true, totalLatency);
                
            } else {
                // Wait for required number of acknowledgments
                AtomicInteger acknowledged = new AtomicInteger(0);
                AtomicInteger replicaId = new AtomicInteger(-1);
                
                futures.forEach(future -> future.thenAccept(result -> {
                    if (result.success()) {
                        acknowledged.incrementAndGet();
                        replicaId.compareAndSet(-1, result.replicaId());
                    }
                }));
                
                // Poll until quorum reached or timeout
                long deadline = System.currentTimeMillis() + 150; // 150ms timeout
                while (acknowledged.get() < requiredAcks) {
                    if (System.currentTimeMillis() > deadline) {
                        long totalLatency = System.currentTimeMillis() - startTime;
                        return new WriteResult(-1, false, totalLatency, 
                            "Timeout: only " + acknowledged.get() + "/" + requiredAcks + " acks");
                    }
                    Thread.onSpinWait();
                }
                
                long totalLatency = System.currentTimeMillis() - startTime;
                return new WriteResult(replicaId.get(), true, totalLatency);
            }
            
        } catch (TimeoutException e) {
            long totalLatency = System.currentTimeMillis() - startTime;
            return new WriteResult(-1, false, totalLatency, "Timeout waiting for replicas");
        } catch (Exception e) {
            long totalLatency = System.currentTimeMillis() - startTime;
            return new WriteResult(-1, false, totalLatency, e.getMessage());
        }
    }
    
    public ReadResult read(long messageId, ConsistencyLevel level) {
        long startTime = System.currentTimeMillis();
        int requiredResponses = level.getRequiredAcks(replicationFactor);
        
        List<CompletableFuture<ReadResult>> futures = replicas.stream()
            .map(replica -> replica.read(messageId))
            .collect(Collectors.toList());
        
        try {
            if (level == ConsistencyLevel.ONE) {
                ReadResult result = (ReadResult) CompletableFuture.anyOf(
                    futures.toArray(new CompletableFuture[0])
                ).get(100, TimeUnit.MILLISECONDS);
                
                return result;
                
            } else {
                // Wait for required number of responses
                List<ReadResult> results = new ArrayList<>();
                
                for (CompletableFuture<ReadResult> future : futures) {
                    try {
                        ReadResult result = future.get(50, TimeUnit.MILLISECONDS);
                        results.add(result);
                        if (results.size() >= requiredResponses) {
                            break;
                        }
                    } catch (Exception ignored) {
                        // Skip failed replicas
                    }
                }
                
                if (results.size() < requiredResponses) {
                    return new ReadResult(-1, null, 
                        System.currentTimeMillis() - startTime);
                }
                
                // Return the most recent version (highest message ID wins)
                return results.stream()
                    .filter(r -> r.message().isPresent())
                    .findFirst()
                    .orElse(results.get(0));
            }
            
        } catch (Exception e) {
            return new ReadResult(-1, null, System.currentTimeMillis() - startTime);
        }
    }
    
    public List<ReplicaNode> getReplicas() {
        return replicas;
    }
}
