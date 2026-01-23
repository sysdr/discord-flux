package com.flux.gateway;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * LEAK #2: Unbounded DirectByteBuffer pool.
 * Under burst traffic, pool grows indefinitely and never shrinks.
 */
public class BufferPool {
    
    private static final int BUFFER_SIZE = 16384; // 16KB
    private static final ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    
    // BUG: No max size limit!
    // private static final int MAX_POOL_SIZE = 10_000;
    
    public static ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer != null) {
            buffer.clear();
            return buffer;
        }
        
        // Allocate new DirectByteBuffer (off-heap)
        return ByteBuffer.allocateDirect(BUFFER_SIZE);
    }
    
    public static void release(ByteBuffer buffer) {
        if (buffer != null) {
            buffer.clear();
            pool.offer(buffer); // BUG: Always add back, no size check
        }
    }
    
    public static int poolSize() {
        return pool.size();
    }
    
    public static long getDirectMemoryUsed() {
        return ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
            .stream()
            .filter(p -> p.getName().equals("direct"))
            .mapToLong(BufferPoolMXBean::getMemoryUsed)
            .sum();
    }
    
    public static long getDirectMemoryCount() {
        return ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
            .stream()
            .filter(p -> p.getName().equals("direct"))
            .mapToLong(BufferPoolMXBean::getCount)
            .sum();
    }
}
