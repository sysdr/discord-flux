package com.flux.gateway.websocket;

import com.flux.gateway.ConnectionManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

public class WebSocketHandler {
    private static final String WEBSOCKET_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final ConnectionManager connectionManager;
    
    public WebSocketHandler(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    public String handleHandshake(SocketChannel channel, String request) throws Exception {
        // Parse WebSocket key from HTTP headers
        var lines = request.split("\r\n");
        String wsKey = null;
        
        for (var line : lines) {
            if (line.startsWith("Sec-WebSocket-Key:")) {
                wsKey = line.substring(line.indexOf(":") + 1).trim();
                break;
            }
        }
        
        if (wsKey == null) {
            throw new IllegalArgumentException("Missing Sec-WebSocket-Key header");
        }
        
        // Generate accept key
        var acceptKey = generateAcceptKey(wsKey);
        
        // Send handshake response
        var response = "HTTP/1.1 101 Switching Protocols\r\n" +
                      "Upgrade: websocket\r\n" +
                      "Connection: Upgrade\r\n" +
                      "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
        
        channel.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
        
        // Generate connection ID and register
        var connectionId = UUID.randomUUID().toString();
        connectionManager.addConnection(connectionId, channel);
        
        return connectionId;
    }
    
    public void handleFrame(String connectionId, SocketChannel channel, ByteBuffer buffer) throws IOException {
        // Simple echo server - read frame and echo it back
        var opcode = buffer.get(0) & 0x0F;
        
        if (opcode == 0x8) { // Close frame
            connectionManager.removeConnection(connectionId);
            channel.close();
            return;
        }
        
        if (opcode == 0x9) { // Ping frame
            // Send pong
            var pongFrame = ByteBuffer.wrap(new byte[]{(byte) 0x8A, 0x00});
            channel.write(pongFrame);
            return;
        }
        
        // Echo the message back
        if (buffer.remaining() > 2) {
            var payloadLength = buffer.get(1) & 0x7F;
            if (payloadLength < 126) {
                var payload = new byte[payloadLength];
                var mask = new byte[4];
                buffer.position(2);
                buffer.get(mask);
                buffer.get(payload);
                
                // Unmask payload
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                }
                
                // Echo back (unmasked for server->client)
                var response = ByteBuffer.allocate(2 + payload.length);
                response.put((byte) 0x81); // Text frame, FIN bit set
                response.put((byte) payload.length);
                response.put(payload);
                response.flip();
                
                channel.write(response);
            }
        }
    }
    
    private String generateAcceptKey(String wsKey) throws Exception {
        var digest = MessageDigest.getInstance("SHA-1");
        var hash = digest.digest((wsKey + WEBSOCKET_MAGIC).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
