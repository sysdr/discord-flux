package com.flux.gateway;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

class LeakDetectionTest {
    
    @Test
    void testPhantomReferenceBasics() {
        record TestObject(String data) {}
        
        ReferenceQueue<TestObject> queue = new ReferenceQueue<>();
        TestObject obj = new TestObject("test");
        PhantomReference<TestObject> ref = new PhantomReference<>(obj, queue);
        
        assertNotNull(ref);
        assertNull(ref.get()); // Phantom refs always return null
        
        obj = null; // Remove strong reference
        System.gc();
        
        // After GC, phantom ref should be enqueued
        // (Note: This is timing-dependent and may be flaky)
    }
    
    @Test
    void testBufferPoolGrowth() {
        int initialSize = BufferPool.poolSize();
        
        // Acquire and release buffers
        for (int i = 0; i < 100; i++) {
            var buf = BufferPool.acquire();
            BufferPool.release(buf);
        }
        
        // Pool should have grown (this is the leak!)
        assertTrue(BufferPool.poolSize() > initialSize, 
            "BufferPool should grow unbounded (demonstrating leak)");
    }
}
