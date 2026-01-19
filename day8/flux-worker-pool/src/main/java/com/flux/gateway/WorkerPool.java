package com.flux.gateway;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Worker pool implementation using Virtual Threads (Java 21+).
 * Decouples I/O thread from CPU-bound message processing.
 */
public class WorkerPool {
    private final BlockingQueue<Task> taskQueue;
    private final ExecutorService workerExecutor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong processedTasks = new AtomicLong();
    private final AtomicLong rejectedTasks = new AtomicLong();
    
    // For latency tracking (p50, p99)
    private final ConcurrentLinkedQueue<Long> latencySamples = new ConcurrentLinkedQueue<>();
    private static final int MAX_SAMPLES = 10000; // Increased to keep more historical data

    public WorkerPool(int queueCapacity) {
        this.taskQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        
        // Start consumer thread (also a Virtual Thread)
        Thread.ofVirtual().name("queue-consumer").start(this::consumeLoop);
    }

    /**
     * Enqueue a task from the I/O thread.
     * Non-blocking: returns immediately even if queue is full.
     */
    public void submit(Task task) {
        if (!taskQueue.offer(task)) {
            // Queue full → reject
            rejectedTasks.incrementAndGet();
            Metrics.incrementCounter("worker_pool.rejected");
            System.err.println("⚠️  Task rejected - queue full");
        } else {
            Metrics.setGauge("worker_pool.queue_depth", taskQueue.size());
        }
    }

    /**
     * Consumer loop: pulls tasks and dispatches to Virtual Thread workers.
     */
    private void consumeLoop() {
        while (running.get()) {
            try {
                Task task = taskQueue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    workerExecutor.submit(() -> processTask(task));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Worker logic: process one task.
     * This runs on a Virtual Thread.
     */
    private void processTask(Task task) {
        long startNanos = System.nanoTime();
        
        try {
            // Simulate message processing (parse JSON, validate, etc.)
            String payload = task.payloadAsString();
            
            // Simple opcode routing using switch expression
            String response = switch (payload.trim()) {
                case "PING" -> "PONG";
                case "HELLO" -> "WELCOME";
                case "SLOW" -> {
                    // Slow processing for testing queue depth
                    Thread.sleep(50); // 50ms processing time
                    yield "SLOW_ACK";
                }
                default -> {
                    // Simulate CPU work (e.g., JSON parsing, validation)
                    Thread.sleep(5); // 5ms average processing time
                    yield "ACK:" + payload.hashCode();
                }
            };
            
            processedTasks.incrementAndGet();
            Metrics.incrementCounter("worker_pool.processed");
            
            // Track latency
            long latencyMicros = (System.nanoTime() - startNanos) / 1_000;
            recordLatency(latencyMicros);
            
        } catch (Exception e) {
            Metrics.incrementCounter("worker_pool.errors");
            System.err.println("❌ Worker error: " + e.getMessage());
        }
    }

    private void recordLatency(long micros) {
        if (latencySamples.size() > MAX_SAMPLES) {
            latencySamples.poll(); // Drop oldest
        }
        latencySamples.offer(micros);
    }

    public long getProcessedCount() {
        return processedTasks.get();
    }

    public long getRejectedCount() {
        return rejectedTasks.get();
    }

    public int getQueueDepth() {
        return taskQueue.size();
    }

    public long getP99Latency() {
        var samples = latencySamples.stream().sorted().toList();
        if (samples.isEmpty()) return 0;
        int p99Index = (int) (samples.size() * 0.99);
        return samples.get(Math.min(p99Index, samples.size() - 1));
    }

    public long getP50Latency() {
        var samples = latencySamples.stream().sorted().toList();
        if (samples.isEmpty()) return 0;
        return samples.get(samples.size() / 2);
    }

    public void shutdown() {
        running.set(false);
        workerExecutor.shutdown();
        try {
            workerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            workerExecutor.shutdownNow();
        }
    }
}
