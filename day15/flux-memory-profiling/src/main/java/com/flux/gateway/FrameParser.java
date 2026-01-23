package com.flux.gateway;

import java.nio.ByteBuffer;

/**
 * LEAK #3: ThreadLocal accumulation with Virtual Threads.
 * Each carrier thread accumulates ByteBuffers from all virtual threads.
 */
public class FrameParser {
    
    private static final int PARSE_BUFFER_SIZE = 65536; // 64KB
    
    // BUG: Never cleaned up with ThreadLocal.remove()
    private static final ThreadLocal<ByteBuffer> parseBuffer = 
        ThreadLocal.withInitial(() -> {
            System.out.println("[FrameParser] Allocating parse buffer for thread: " + 
                Thread.currentThread().getName());
            return ByteBuffer.allocate(PARSE_BUFFER_SIZE);
        });
    
    public static ByteBuffer getParseBuffer() {
        return parseBuffer.get();
    }
    
    public static void cleanup() {
        // This SHOULD be called in finally block but isn't
        parseBuffer.remove();
    }
    
    public static int estimateThreadLocalMemory() {
        // Rough estimate: carrier threads × buffer size
        int carrierThreads = Runtime.getRuntime().availableProcessors();
        return carrierThreads * PARSE_BUFFER_SIZE;
    }
}
