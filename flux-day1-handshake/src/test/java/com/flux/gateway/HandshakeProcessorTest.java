package com.flux.gateway;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeProcessorTest {
    
    private final HandshakeProcessor processor = new HandshakeProcessor();
    
    @Test
    void testCompleteHandshakeParsing() {
        String request = "GET /gateway HTTP/1.1\r\n" +
                        "Host: flux.chat\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n";
        
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put(request.getBytes(StandardCharsets.UTF_8));
        
        var result = processor.parse(buffer);
        
        assertTrue(result.complete());
        assertEquals("dGhlIHNhbXBsZSBub25jZQ==", result.webSocketKey());
    }
    
    @Test
    void testIncompleteHandshake() {
        String request = "GET /gateway HTTP/1.1\r\n" +
                        "Host: flux.chat\r\n";
        
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put(request.getBytes(StandardCharsets.UTF_8));
        
        var result = processor.parse(buffer);
        
        assertFalse(result.complete());
    }
    
    @Test
    void testAcceptKeyComputation() {
        String clientKey = "dGhlIHNhbXBsZSBub25jZQ==";
        String acceptKey = processor.computeAcceptKey(clientKey);
        
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", acceptKey);
    }
}
