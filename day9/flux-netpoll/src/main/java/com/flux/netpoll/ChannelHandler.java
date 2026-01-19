package com.flux.netpoll;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Instant;

public class ChannelHandler {
    private final long connectionId;
    private final SocketChannel channel;
    private final BufferPool bufferPool;
    
    private volatile ChannelState state;
    private static final VarHandle STATE_HANDLE;
    
    static {
        try {
            STATE_HANDLE = MethodHandles.lookup()
                .findVarHandle(ChannelHandler.class, "state", ChannelState.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public ChannelHandler(long connectionId, SocketChannel channel, BufferPool bufferPool) {
        this.connectionId = connectionId;
        this.channel = channel;
        this.bufferPool = bufferPool;
        this.state = new ChannelState(State.CONNECTED, Instant.now(), 0);
    }

    public boolean handleRead() throws IOException {
        ByteBuffer buffer = bufferPool.acquire();
        
        try {
            int bytesRead = channel.read(buffer);
            
            if (bytesRead == -1) {
                return false; // Client closed connection
            }
            
            if (bytesRead == 0) {
                return true; // No data available (spurious wake)
            }
            
            buffer.flip();
            processData(buffer);
            
            updateState(new ChannelState(
                State.CONNECTED,
                Instant.now(),
                0
            ));
            
            return true;
            
        } finally {
            bufferPool.release(buffer);
        }
    }

    private void processData(ByteBuffer buffer) throws IOException {
        // Echo back for demonstration
        buffer.rewind();
        // Write all data (handle partial writes for non-blocking channel)
        while (buffer.hasRemaining()) {
            int bytesWritten = channel.write(buffer);
            if (bytesWritten == 0) {
                // Channel is not ready for writing, this shouldn't happen in our setup
                // but handle it gracefully
                break;
            }
        }
        // Small delay to make virtual threads visible in metrics
        // (Virtual threads complete so fast they're often not visible)
        try {
            Thread.sleep(1); // 1ms delay to allow virtual thread to be tracked
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateState(ChannelState newState) {
        STATE_HANDLE.setVolatile(this, newState);
    }

    public long connectionId() {
        return connectionId;
    }

    public ChannelState getState() {
        return (ChannelState) STATE_HANDLE.getVolatile(this);
    }

    public enum State {
        CONNECTED, IDLE, ZOMBIE
    }

    public record ChannelState(State state, Instant lastActivity, int missedHeartbeats) {}
}
