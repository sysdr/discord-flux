package com.flux.gateway;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Represents a single WebSocket connection.
 * Contains references to DirectByteBuffers that leak when session isn't cleaned up.
 */
public record Session(String id, SocketChannel channel) {
    
    // LEAK #2: These buffers allocated from pool, never returned
    private static final ThreadLocal<ByteBuffer> readBuffer = 
        ThreadLocal.withInitial(() -> BufferPool.acquire());
    
    private static final ThreadLocal<ByteBuffer> writeBuffer = 
        ThreadLocal.withInitial(() -> BufferPool.acquire());
    
    public ByteBuffer getReadBuffer() {
        return readBuffer.get();
    }
    
    public ByteBuffer getWriteBuffer() {
        return writeBuffer.get();
    }
    
    public void cleanup() {
        // This SHOULD be called but isn't in LeakyGateway
        BufferPool.release(readBuffer.get());
        BufferPool.release(writeBuffer.get());
        readBuffer.remove();
        writeBuffer.remove();
    }
}
