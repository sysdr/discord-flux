package com.flux.gateway;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

public final class TestClient {
    public static void main(String[] args) throws Exception {
        int numClients = Integer.parseInt(args.length > 0 ? args[0] : "10");
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            Thread.ofVirtual().start(() -> {
                try {
                    runClient(clientId);
                } catch (Exception e) {
                    System.err.println("Client " + clientId + " failed: " + e.getMessage());
                }
            });
            
            // Stagger connections
            Thread.sleep(100);
        }
        
        System.out.println("Started " + numClients + " clients");
        Thread.sleep(Long.MAX_VALUE);
    }
    
    private static void runClient(int id) throws Exception {
        Socket socket = new Socket("localhost", 8080);
        OutputStream out = socket.getOutputStream();
        
        // Send WebSocket handshake
        String handshake = """
            GET /gateway HTTP/1.1\r
            Host: localhost:8080\r
            Upgrade: websocket\r
            Connection: Upgrade\r
            Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r
            Sec-WebSocket-Version: 13\r
            \r
            """;
        out.write(handshake.getBytes());
        out.flush();
        
        // Wait for handshake response
        Thread.sleep(500);
        
        // Send heartbeat ACKs every 25 seconds
        while (true) {
            sendHeartbeatAck(out);
            LockSupport.parkNanos(25_000_000_000L); // 25s
        }
    }
    
    private static void sendHeartbeatAck(OutputStream out) throws Exception {
        // WebSocket frame: Opcode 11 (Heartbeat ACK)
        String payload = "{\"op\":11}";
        byte[] payloadBytes = payload.getBytes();
        
        ByteBuffer frame = ByteBuffer.allocate(6 + payloadBytes.length);
        frame.put((byte) 0x81); // FIN + Text
        frame.put((byte) (0x80 | payloadBytes.length)); // Masked + length
        frame.putInt(0x12345678); // Mask key
        
        // Mask payload
        for (int i = 0; i < payloadBytes.length; i++) {
            frame.put((byte) (payloadBytes[i] ^ ((0x12345678 >> ((3 - i % 4) * 8)) & 0xFF)));
        }
        
        out.write(frame.array());
        out.flush();
    }
}
