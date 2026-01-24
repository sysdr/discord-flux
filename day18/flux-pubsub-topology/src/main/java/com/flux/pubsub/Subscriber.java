package com.flux.pubsub;

/**
 * Subscriber interface for receiving published messages.
 * Implementations must handle backpressure via the Ring Buffer.
 */
public interface Subscriber {
    /**
     * Called when a message is published to a subscribed topic.
     * @param data The message payload
     * @return true if accepted, false if buffer full (message dropped)
     */
    boolean onMessage(byte[] data);
    
    /**
     * Unique identifier for this subscriber.
     */
    String subscriberId();
    
    /**
     * Number of messages currently in buffer.
     */
    int pendingCount();
    
    /**
     * Total messages dropped due to buffer overflow.
     */
    long droppedCount();
}
