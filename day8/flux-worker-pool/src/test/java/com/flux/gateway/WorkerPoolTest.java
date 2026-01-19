package com.flux.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class WorkerPoolTest {
    private WorkerPool pool;

    @AfterEach
    void cleanup() {
        if (pool != null) {
            pool.shutdown();
        }
    }

    @Test
    void testBasicTaskProcessing() throws InterruptedException {
        pool = new WorkerPool(100);
        
        ByteBuffer buffer = ByteBuffer.wrap("PING".getBytes());
        Task task = new Task(1L, buffer, System.nanoTime(), 
                            new InetSocketAddress("localhost", 8080));
        
        pool.submit(task);
        Thread.sleep(100); // Wait for processing
        
        assertTrue(pool.getProcessedCount() > 0, "Task should be processed");
    }

    @Test
    void testQueueRejection() throws InterruptedException {
        pool = new WorkerPool(2); // Very small queue
        
        // Flood with tasks
        for (int i = 0; i < 100; i++) {
            ByteBuffer buffer = ByteBuffer.wrap(("MSG" + i).getBytes());
            Task task = new Task(i, buffer, System.nanoTime(),
                                new InetSocketAddress("localhost", 8080));
            pool.submit(task);
        }
        
        Thread.sleep(500);
        assertTrue(pool.getRejectedCount() > 0, "Should reject some tasks");
    }

    @Test
    void testLatencyTracking() throws InterruptedException {
        pool = new WorkerPool(100);
        
        for (int i = 0; i < 10; i++) {
            ByteBuffer buffer = ByteBuffer.wrap("TEST".getBytes());
            Task task = new Task(i, buffer, System.nanoTime(),
                                new InetSocketAddress("localhost", 8080));
            pool.submit(task);
        }
        
        Thread.sleep(200);
        
        long p50 = pool.getP50Latency();
        long p99 = pool.getP99Latency();
        
        assertTrue(p50 > 0, "p50 latency should be recorded");
        assertTrue(p99 >= p50, "p99 should be >= p50");
    }
}
