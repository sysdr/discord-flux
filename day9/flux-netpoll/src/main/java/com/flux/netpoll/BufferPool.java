package com.flux.netpoll;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferPool {
    private static final int BUFFER_SIZE = 8192;
    private final ConcurrentLinkedQueue<ByteBuffer> pool;
    private final int maxPoolSize;

    public BufferPool(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        this.pool = new ConcurrentLinkedQueue<>();
        
        // Pre-allocate some buffers
        for (int i = 0; i < Math.min(100, maxPoolSize); i++) {
            pool.offer(ByteBuffer.allocateDirect(BUFFER_SIZE));
        }
    }

    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            return ByteBuffer.allocateDirect(BUFFER_SIZE);
        }
        buffer.clear();
        return buffer;
    }

    public void release(ByteBuffer buffer) {
        if (pool.size() < maxPoolSize) {
            buffer.clear();
            pool.offer(buffer);
        }
        // Otherwise let it be GC'd
    }

    public int poolSize() {
        return pool.size();
    }
}
