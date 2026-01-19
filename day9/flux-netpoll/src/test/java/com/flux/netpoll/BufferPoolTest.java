package com.flux.netpoll;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class BufferPoolTest {
    
    @Test
    void testBufferReuseReducesAllocation() {
        BufferPool pool = new BufferPool(1); // Small pool to ensure reuse
        
        // Acquire all pre-allocated buffers
        ByteBuffer[] temp = new ByteBuffer[100];
        for (int i = 0; i < 100; i++) {
            temp[i] = pool.acquire();
        }
        // Release them all
        for (ByteBuffer buf : temp) {
            pool.release(buf);
        }
        
        // Now acquire one - should come from pool
        ByteBuffer buf1 = pool.acquire();
        assertNotNull(buf1);
        
        pool.release(buf1);
        
        ByteBuffer buf2 = pool.acquire();
        // With pool size 1, we should get the same buffer back
        assertSame(buf1, buf2, "Should reuse the same buffer from pool");
    }
    
    @Test
    void testPoolSizeLimiting() {
        BufferPool pool = new BufferPool(5);
        
        ByteBuffer[] buffers = new ByteBuffer[10];
        for (int i = 0; i < 10; i++) {
            buffers[i] = pool.acquire();
        }
        
        for (ByteBuffer buf : buffers) {
            pool.release(buf);
        }
        
        assertTrue(pool.poolSize() <= 5, "Pool should not exceed max size");
    }
}
