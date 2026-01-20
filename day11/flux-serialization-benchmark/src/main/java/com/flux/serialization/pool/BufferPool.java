package com.flux.serialization.pool;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferPool {
    private final Queue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    private final int bufferSize;
    private final boolean direct;
    
    public BufferPool(int bufferSize, boolean direct) {
        this.bufferSize = bufferSize;
        this.direct = direct;
    }
    
    public ByteBuffer acquire() {
        ByteBuffer buf = pool.poll();
        if (buf == null) {
            buf = direct ? ByteBuffer.allocateDirect(bufferSize) 
                         : ByteBuffer.allocate(bufferSize);
        }
        buf.clear();
        return buf;
    }
    
    public void release(ByteBuffer buf) {
        if (buf.capacity() == bufferSize) {
            buf.clear();
            pool.offer(buf);
        }
    }
    
    public int size() {
        return pool.size();
    }
}
