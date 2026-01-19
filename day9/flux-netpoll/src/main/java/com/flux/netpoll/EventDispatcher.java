package com.flux.netpoll;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class EventDispatcher {
    private final ExecutorService virtualExecutor;
    private final AtomicInteger activeThreads = new AtomicInteger(0);

    public EventDispatcher() {
        // Virtual Thread executor (Java 21+)
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void dispatch(Runnable task) {
        activeThreads.incrementAndGet();
        virtualExecutor.submit(() -> {
            try {
                task.run();
            } finally {
                activeThreads.decrementAndGet();
            }
        });
    }

    public int getActiveThreads() {
        return activeThreads.get();
    }

    public void shutdown() {
        virtualExecutor.shutdown();
        try {
            if (!virtualExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                virtualExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualExecutor.shutdownNow();
        }
    }
}
