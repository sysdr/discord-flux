package com.flux.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load testing tool using Virtual Threads to simulate thousands of concurrent clients.
 * Each client:
 * 1. Connects to the gateway
 * 2. Performs handshake (sends FLUX_HELLO, expects FLUX_READY)
 * 3. Sends N messages and waits for echoes
 * 4. Closes connection
 */
public class LoadGenerator {
    
    private static final String HOST = "localhost";
    private static final int PORT = 9090;
    
    public static void main(String[] args) {
        final int numConnections = args.length >= 1 ? Integer.parseInt(args[0]) : 100;
        final int messagesPerConnection = args.length >= 2 ? Integer.parseInt(args[1]) : 10;
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║      FLUX LOAD GENERATOR (Day 3)      ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Target: " + HOST + ":" + PORT);
        System.out.println("Connections: " + numConnections);
        System.out.println("Messages per connection: " + messagesPerConnection);
        System.out.println();
        
        AtomicInteger successfulConnections = new AtomicInteger(0);
        AtomicInteger failedConnections = new AtomicInteger(0);
        AtomicInteger totalMessages = new AtomicInteger(0);
        
        CountDownLatch latch = new CountDownLatch(numConnections);
        
        long startTime = System.currentTimeMillis();
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numConnections; i++) {
                final int clientId = i;
                executor.submit(() -> {
                    try {
                        runClient(clientId, messagesPerConnection);
                        successfulConnections.incrementAndGet();
                    } catch (Exception e) {
                        failedConnections.incrementAndGet();
                        System.err.println("Client #" + clientId + " failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                        final int completed = numConnections - (int) latch.getCount();
                        final int total = numConnections;
                        if (completed % 100 == 0) {
                            System.out.printf("Progress: %d/%d clients completed\n", completed, total);
                        }
                    }
                });
            }
            
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Load test interrupted");
            return;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║            RESULTS SUMMARY             ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println("Duration: " + duration + " ms");
        System.out.println("Successful connections: " + successfulConnections.get());
        System.out.println("Failed connections: " + failedConnections.get());
        System.out.println("Connections/sec: " + (successfulConnections.get() * 1000.0 / duration));
        System.out.println();
        
        if (failedConnections.get() > 0) {
            System.err.println("⚠ Some connections failed. Check server logs.");
        } else {
            System.out.println("✓ All connections successful!");
        }
    }
    
    private static void runClient(int clientId, int numMessages) throws IOException {
        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(5000); // 5 second timeout
            
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            
            // Handshake
            sendMessage(out, "FLUX_HELLO");
            String response = receiveMessage(in);
            if (!"FLUX_READY".equals(response)) {
                throw new IOException("Handshake failed: expected FLUX_READY, got " + response);
            }
            
            // Send messages
            for (int i = 0; i < numMessages; i++) {
                String message = "Test message " + i + " from client " + clientId;
                sendMessage(out, message);
                String echo = receiveMessage(in);
                if (!echo.startsWith("ECHO: ")) {
                    throw new IOException("Invalid echo response: " + echo);
                }
            }
        }
    }
    
    private static void sendMessage(OutputStream out, String message) throws IOException {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + payload.length);
        buffer.putInt(payload.length);
        buffer.put(payload);
        out.write(buffer.array());
        out.flush();
    }
    
    private static String receiveMessage(InputStream in) throws IOException {
        // Read length header
        byte[] lengthBytes = new byte[4];
        int totalRead = 0;
        while (totalRead < 4) {
            int read = in.read(lengthBytes, totalRead, 4 - totalRead);
            if (read == -1) {
                throw new IOException("Connection closed while reading length");
            }
            totalRead += read;
        }
        
        int length = ByteBuffer.wrap(lengthBytes).getInt();
        if (length < 0 || length > 1_048_576) {
            throw new IOException("Invalid message length: " + length);
        }
        
        // Read payload
        byte[] payload = new byte[length];
        totalRead = 0;
        while (totalRead < length) {
            int read = in.read(payload, totalRead, length - totalRead);
            if (read == -1) {
                throw new IOException("Connection closed while reading payload");
            }
            totalRead += read;
        }
        
        return new String(payload, StandardCharsets.UTF_8);
    }
}
