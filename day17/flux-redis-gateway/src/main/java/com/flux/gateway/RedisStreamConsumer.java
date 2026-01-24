package com.flux.gateway;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.*;
import io.lettuce.core.XReadArgs.StreamOffset;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumes messages from Redis Stream using Virtual Threads.
 * Each consumer runs in its own virtual thread with blocking XREAD.
 */
public class RedisStreamConsumer implements Runnable {
    private final String streamKey;
    private final RingBuffer buffer;
    private final StatefulRedisConnection<String, String> connection;
    private final AtomicLong messagesRead = new AtomicLong(0);
    private final AtomicLong messagesDropped = new AtomicLong(0);
    private volatile boolean running = true;
    
    public RedisStreamConsumer(String redisUrl, String streamKey, RingBuffer buffer) {
        RedisClient client = RedisClient.create(redisUrl);
        this.connection = client.connect();
        this.streamKey = streamKey;
        this.buffer = buffer;
    }
    
    @Override
    public void run() {
        RedisCommands<String, String> commands = connection.sync();
        String lastId = "0-0"; // Start from beginning
        
        System.out.println("[Consumer] Started for stream: " + streamKey);
        
        while (running && !Thread.interrupted()) {
            try {
                // XREAD BLOCK 1000: blocks up to 1 second
                List<StreamMessage<String, String>> messages = commands.xread(
                    XReadArgs.Builder.block(1000),
                    StreamOffset.from(streamKey, lastId)
                );
                
                if (messages != null && !messages.isEmpty()) {
                    for (StreamMessage<String, String> msg : messages) {
                        String payload = msg.getBody().get("data");
                        
                        if (!buffer.offer(payload)) {
                            // Backpressure: buffer full, drop message
                            messagesDropped.incrementAndGet();
                        } else {
                            messagesRead.incrementAndGet();
                        }
                        
                        lastId = msg.getId();
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[Consumer] Error reading stream: " + e.getMessage());
                    try {
                        Thread.sleep(1000); // Backoff on error
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        connection.close();
        System.out.println("[Consumer] Stopped. Read: " + messagesRead.get() + 
                          ", Dropped: " + messagesDropped.get());
    }
    
    public void stop() {
        running = false;
    }
    
    public long getMessagesRead() {
        return messagesRead.get();
    }
    
    public long getMessagesDropped() {
        return messagesDropped.get();
    }
}
