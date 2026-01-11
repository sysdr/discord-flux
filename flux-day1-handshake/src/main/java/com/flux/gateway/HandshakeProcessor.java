package com.flux.gateway;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public record HandshakeProcessor() {
    
    private static final byte[] MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CRLF = new byte[]{'\r', '\n'};
    private static final byte[] DOUBLE_CRLF = new byte[]{'\r', '\n', '\r', '\n'};
    
    public record HandshakeResult(boolean complete, String webSocketKey) {}
    
    public HandshakeResult parse(ByteBuffer buffer) {
        buffer.flip();
        
        // Check if we have complete headers (ends with \r\n\r\n)
        if (!hasCompleteHeaders(buffer)) {
            buffer.compact();
            return new HandshakeResult(false, null);
        }
        
        // Extract Sec-WebSocket-Key without String allocation until necessary
        String key = extractWebSocketKey(buffer);
        buffer.compact();
        
        if (key == null) {
            return new HandshakeResult(false, null);
        }
        
        return new HandshakeResult(true, key);
    }
    
    private boolean hasCompleteHeaders(ByteBuffer buffer) {
        int limit = buffer.limit();
        if (limit < 4) return false;
        
        for (int i = 0; i <= limit - 4; i++) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n' &&
                buffer.get(i + 2) == '\r' && buffer.get(i + 3) == '\n') {
                return true;
            }
        }
        return false;
    }
    
    private String extractWebSocketKey(ByteBuffer buffer) {
        // Convert to String only for parsing (in production, use zero-copy byte scanning)
        byte[] headerBytes = new byte[buffer.remaining()];
        buffer.get(headerBytes);
        String headers = new String(headerBytes, StandardCharsets.UTF_8);
        
        String[] lines = headers.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("Sec-WebSocket-Key:")) {
                return line.substring(18).trim();
            }
        }
        return null;
    }
    
    public String computeAcceptKey(String clientKey) {
        try {
            byte[] keyBytes = clientKey.getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[keyBytes.length + MAGIC_STRING.length];
            System.arraycopy(keyBytes, 0, combined, 0, keyBytes.length);
            System.arraycopy(MAGIC_STRING, 0, combined, keyBytes.length, MAGIC_STRING.length);
            
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(combined);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute accept key", e);
        }
    }
    
    public ByteBuffer createHandshakeResponse(String acceptKey) {
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                         "Upgrade: websocket\r\n" +
                         "Connection: Upgrade\r\n" +
                         "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
        
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.wrap(responseBytes);
    }
}
