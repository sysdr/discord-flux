package com.flux.pubsub.core;

import java.util.concurrent.atomic.AtomicLong;

public class BoundedEventBuffer {
    private final GuildEvent[] buffer;
    private final int capacity;
    private final AtomicLong writeIndex = new AtomicLong(0);
    private final AtomicLong readIndex = new AtomicLong(0);
    private final AtomicLong droppedCount = new AtomicLong(0);

    public BoundedEventBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new GuildEvent[capacity];
    }

    // Offer event (drops oldest if full)
    public boolean offer(GuildEvent event) {
        long write = writeIndex.get();
        long read = readIndex.get();
        
        // Check if buffer is full
        if (write - read >= capacity) {
            // Drop oldest message
            readIndex.compareAndSet(read, read + 1);
            droppedCount.incrementAndGet();
        }
        
        // Write new message
        int index = (int) (write % capacity);
        buffer[index] = event;
        writeIndex.incrementAndGet();
        return true;
    }

    // Poll event (non-blocking)
    public GuildEvent poll() {
        long read = readIndex.get();
        long write = writeIndex.get();
        
        if (read >= write) {
            return null; // Buffer empty
        }
        
        int index = (int) (read % capacity);
        GuildEvent event = buffer[index];
        readIndex.incrementAndGet();
        return event;
    }

    public int size() {
        long write = writeIndex.get();
        long read = readIndex.get();
        return (int) Math.max(0, write - read);
    }

    public long getDroppedCount() {
        return droppedCount.get();
    }

    public void reset() {
        writeIndex.set(0);
        readIndex.set(0);
        droppedCount.set(0);
    }
}
