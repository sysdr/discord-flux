package com.flux.gateway;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class GatewayConnection {
    private final SessionId sessionId;
    private final SocketChannel socketChannel;
    private final SelectionKey selectionKey;
    private final Set<GuildId> subscribedGuilds;
    private final ArrayBlockingQueue<ByteBuffer> writeQueue;
    private final AtomicLong messagesSent;
    private final AtomicLong bytesWritten;
    private volatile boolean slowConsumer;
    
    private static final int MAX_QUEUED_MESSAGES = 1000;
    
    public GatewayConnection(
        SessionId sessionId, 
        SocketChannel socketChannel, 
        SelectionKey selectionKey
    ) {
        this.sessionId = sessionId;
        this.socketChannel = socketChannel;
        this.selectionKey = selectionKey;
        this.subscribedGuilds = ConcurrentHashMap.newKeySet();
        this.writeQueue = new ArrayBlockingQueue<>(MAX_QUEUED_MESSAGES);
        this.messagesSent = new AtomicLong(0);
        this.bytesWritten = new AtomicLong(0);
        this.slowConsumer = false;
    }
    
    public SessionId sessionId() {
        return sessionId;
    }
    
    public SocketChannel socketChannel() {
        return socketChannel;
    }
    
    public Set<GuildId> subscribedGuilds() {
        return subscribedGuilds;
    }
    
    public void subscribeToGuild(GuildId guildId) {
        subscribedGuilds.add(guildId);
    }
    
    public void unsubscribeFromGuild(GuildId guildId) {
        subscribedGuilds.remove(guildId);
    }
    
    /**
     * Queue a message for writing. Returns false if queue is full (slow consumer).
     */
    public boolean queueWrite(ByteBuffer buffer) {
        boolean offered = writeQueue.offer(buffer);
        if (!offered) {
            slowConsumer = true;
            return false;
        }
        
        // Register interest in OP_WRITE if not already (skip when null, e.g. in tests)
        if (selectionKey != null) {
            synchronized (selectionKey) {
                selectionKey.interestOpsOr(SelectionKey.OP_WRITE);
            }
        }
        
        return true;
    }
    
    /**
     * Drain the write queue. Called by NIO selector thread when OP_WRITE fires.
     */
    public int drainWriteQueue() throws Exception {
        int messagesDrained = 0;
        
        while (true) {
            ByteBuffer buffer = writeQueue.peek();
            if (buffer == null) {
                break; // Queue empty
            }
            
            int written = (socketChannel != null) ? socketChannel.write(buffer) : 0;
            if (written == 0) {
                // TCP send buffer full, try again later
                break;
            }
            
            bytesWritten.addAndGet(written);
            
            if (!buffer.hasRemaining()) {
                // Message fully sent
                writeQueue.poll();
                messagesSent.incrementAndGet();
                messagesDrained++;
            }
        }
        
        // Unregister OP_WRITE if queue is empty
        if (writeQueue.isEmpty() && selectionKey != null) {
            synchronized (selectionKey) {
                selectionKey.interestOpsAnd(~SelectionKey.OP_WRITE);
            }
        }
        
        return messagesDrained;
    }
    
    public int writeQueueDepth() {
        return writeQueue.size();
    }
    
    public long messagesSent() {
        return messagesSent.get();
    }
    
    public long bytesWritten() {
        return bytesWritten.get();
    }
    
    public boolean isSlowConsumer() {
        return slowConsumer;
    }
    
    public void close() {
        try {
            if (socketChannel != null) {
                socketChannel.close();
            }
            writeQueue.clear();
        } catch (Exception e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}
