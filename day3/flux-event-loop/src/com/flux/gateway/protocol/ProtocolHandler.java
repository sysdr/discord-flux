package com.flux.gateway.protocol;

import com.flux.gateway.core.Connection;
import com.flux.gateway.core.ConnectionState;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Handles the Flux wire protocol:
 * Frame format: [4-byte length (big-endian)][payload bytes]
 * 
 * Handshake: Client sends "FLUX_HELLO", server responds "FLUX_READY"
 * Messages: Client sends arbitrary payloads, server echoes back
 */
public class ProtocolHandler {
    
    private static final String HANDSHAKE_CLIENT = "FLUX_HELLO";
    private static final String HANDSHAKE_SERVER = "FLUX_READY";
    private static final int FRAME_HEADER_SIZE = 4;
    
    /**
     * Parse incoming data for a connection.
     * Returns number of complete messages processed.
     */
    public int processIncomingData(Connection conn) {
        var buffer = conn.readBuffer();
        buffer.flip(); // Prepare for reading
        
        int messagesProcessed = 0;
        
        while (buffer.remaining() >= FRAME_HEADER_SIZE) {
            // Peek at length without consuming
            int length = buffer.getInt(buffer.position());
            
            // Validate length (prevent DoS via huge allocations)
            if (length < 0 || length > 1_048_576) { // Max 1MB per message
                conn.transitionTo(ConnectionState.CLOSING);
                buffer.clear();
                return messagesProcessed;
            }
            
            // Check if full frame is available
            if (buffer.remaining() < FRAME_HEADER_SIZE + length) {
                break; // Partial frame, wait for more data
            }
            
            // Consume the frame
            buffer.getInt(); // Consume length header
            byte[] payload = new byte[length];
            buffer.get(payload);
            
            handleMessage(conn, payload);
            messagesProcessed++;
        }
        
        // Compact buffer: move unread bytes to beginning
        buffer.compact();
        return messagesProcessed;
    }
    
    private void handleMessage(Connection conn, byte[] payload) {
        String message = new String(payload, StandardCharsets.UTF_8);
        
        switch (conn.state()) {
            case HANDSHAKE -> {
                if (HANDSHAKE_CLIENT.equals(message)) {
                    conn.transitionTo(ConnectionState.READY);
                    sendMessage(conn, HANDSHAKE_SERVER);
                } else {
                    conn.transitionTo(ConnectionState.CLOSING);
                }
            }
            case READY -> {
                // Echo the message back
                sendMessage(conn, "ECHO: " + message);
            }
            default -> {
                // Ignore messages in other states
            }
        }
    }
    
    /**
     * Queue a message for sending.
     * Creates a framed ByteBuffer and adds to write queue.
     */
    public void sendMessage(Connection conn, String message) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(FRAME_HEADER_SIZE + payload.length);
        frame.putInt(payload.length);
        frame.put(payload);
        frame.flip();
        
        conn.writeQueue().offer(frame);
    }
}
