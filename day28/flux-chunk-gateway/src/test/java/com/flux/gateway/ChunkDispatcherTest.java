package com.flux.gateway;

import com.flux.gateway.dispatcher.ChunkDispatcher;
import com.flux.gateway.protocol.ChunkRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkDispatcherTest {
    
    @Test
    void testEnqueueDequeue() {
        ChunkDispatcher dispatcher = new ChunkDispatcher();
        
        ChunkRequest request = new ChunkRequest(
            "conn-1", "guild-123", "", 100, "nonce-1"
        );
        
        assertTrue(dispatcher.enqueue(request));
        
        ChunkRequest dequeued = dispatcher.dequeue();
        assertNotNull(dequeued);
        assertEquals("guild-123", dequeued.guildId());
    }
    
    @Test
    void testBackpressure() {
        ChunkDispatcher dispatcher = new ChunkDispatcher();
        
        // Fill ring buffer (capacity 8192, so 8192 enqueues fill it)
        int capacity = 8192;
        int i = 0;
        for (; i < capacity; i++) {
            ChunkRequest req = new ChunkRequest(
                "conn-" + i, "guild-" + i, "", 100, "nonce-" + i
            );
            if (!dispatcher.enqueue(req)) {
                break; // Backpressure applied
            }
        }
        assertTrue(i >= 8191, "Should enqueue at least 8191 before backpressure (got " + i + ")");
        
        // Next request should fail (buffer full)
        ChunkRequest overflow = new ChunkRequest(
            "conn-overflow", "guild-overflow", "", 100, "nonce-overflow"
        );
        assertFalse(dispatcher.enqueue(overflow), "Should reject when buffer full");
    }
    
    @Test
    void testUtilization() {
        ChunkDispatcher dispatcher = new ChunkDispatcher();
        
        assertEquals(0.0, dispatcher.getUtilization(), 0.01);
        
        for (int i = 0; i < 4096; i++) {
            dispatcher.enqueue(new ChunkRequest(
                "conn-" + i, "guild-" + i, "", 100, "nonce-" + i
            ));
        }
        
        assertEquals(0.5, dispatcher.getUtilization(), 0.01);
    }
}
