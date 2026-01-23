package com.flux.gateway;

import java.lang.management.*;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector implements Runnable {
    
    private final AtomicLong messageCount = new AtomicLong(0);
    private volatile long lastGcCount = 0;
    private volatile long lastGcTime = 0;
    
    public void incrementMessageCount() {
        messageCount.incrementAndGet();
    }
    
    public long getMessageCount() {
        return messageCount.get();
    }
    
    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(5000);
                collectMetrics();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void collectMetrics() {
        // GC stats
        long totalGcCount = 0;
        long totalGcTime = 0;
        
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            totalGcCount += gc.getCollectionCount();
            totalGcTime += gc.getCollectionTime();
        }
        
        long gcDelta = totalGcCount - lastGcCount;
        long gcTimeDelta = totalGcTime - lastGcTime;
        
        if (gcDelta > 0) {
            System.out.printf("[Metrics] GC: %d collections in last 5s, %d ms total%n", 
                gcDelta, gcTimeDelta);
        }
        
        lastGcCount = totalGcCount;
        lastGcTime = totalGcTime;
    }
    
    public HeapMetrics getHeapMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        long edenUsed = 0;
        long edenMax = 0;
        long oldGenUsed = 0;
        long oldGenMax = 0;
        
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                MemoryUsage usage = pool.getUsage();
                if (pool.getName().contains("Eden")) {
                    edenUsed = usage.getUsed();
                    edenMax = usage.getMax();
                } else if (pool.getName().contains("Old Gen")) {
                    oldGenUsed = usage.getUsed();
                    oldGenMax = usage.getMax();
                }
            }
        }
        
        return new HeapMetrics(
            heapUsage.getUsed(),
            heapUsage.getMax(),
            edenUsed,
            edenMax,
            oldGenUsed,
            oldGenMax,
            BufferPool.getDirectMemoryUsed(),
            BufferPool.getDirectMemoryCount()
        );
    }
    
    public record HeapMetrics(
        long heapUsed,
        long heapMax,
        long edenUsed,
        long edenMax,
        long oldGenUsed,
        long oldGenMax,
        long directMemoryUsed,
        long directMemoryCount
    ) {}
}
