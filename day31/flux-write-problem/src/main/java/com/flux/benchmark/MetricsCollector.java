package com.flux.benchmark;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Collects JVM metrics for dashboard.
 */
public class MetricsCollector {
    
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private long lastGcCount = 0;
    private long lastGcTime = 0;
    
    public MetricsSnapshot snapshot() {
        var heapUsage = memoryMXBean.getHeapMemoryUsage();
        var nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        long gcCount = 0;
        long gcTime = 0;
        
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += gc.getCollectionCount();
            gcTime += gc.getCollectionTime();
        }
        
        var gcCountDelta = gcCount - lastGcCount;
        var gcTimeDelta = gcTime - lastGcTime;
        
        lastGcCount = gcCount;
        lastGcTime = gcTime;
        
        return new MetricsSnapshot(
            heapUsage.getUsed(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed(),
            gcCountDelta,
            gcTimeDelta
        );
    }
    
    public record MetricsSnapshot(
        long heapUsed,
        long heapMax,
        long nonHeapUsed,
        long gcCount,
        long gcTimeMs
    ) {}
}
