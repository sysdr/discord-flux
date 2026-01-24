package com.flux.pubsub;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a WebSocket session subscribing to guild topics.
 * Each subscriber has a bounded ring buffer to handle backpressure.
 */
public class GatewaySubscriber implements Subscriber {
    private final String id;
    private final BoundedRingBuffer buffer;
    private final AtomicLong received = new AtomicLong(0);
    private final Runnable onMessageCallback;
    
    public GatewaySubscriber(String id, int bufferSize, Runnable onMessageCallback) {
        this.id = id;
        this.buffer = new BoundedRingBuffer(bufferSize);
        this.onMessageCallback = onMessageCallback;
    }
    
    @Override
    public boolean onMessage(byte[] data) {
        boolean accepted = buffer.offer(data);
        if (accepted) {
            received.incrementAndGet();
            if (onMessageCallback != null) {
                onMessageCallback.run();
            }
        }
        return accepted;
    }
    
    @Override
    public String subscriberId() {
        return id;
    }
    
    @Override
    public int pendingCount() {
        return buffer.size();
    }
    
    @Override
    public long droppedCount() {
        return buffer.droppedCount();
    }
    
    public byte[] pollMessage() {
        return buffer.poll();
    }
    
    public long receivedCount() {
        return received.get();
    }
}
