package com.flux.gateway;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTest {
    
    public static void main(String[] args) throws Exception {
        int numConnections = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        String host = "localhost";
        int port = 9001;
        
        System.out.println("🔥 Starting load test: " + numConnections + " connections");
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(numConnections);
        AtomicInteger successful = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numConnections; i++) {
            final int connId = i;
            executor.submit(() -> {
                try {
                    performHandshake(host, port, connId);
                    successful.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Connection " + connId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("\n✅ Load test complete!");
        System.out.println("Total connections: " + numConnections);
        System.out.println("Successful: " + successful.get());
        System.out.println("Failed: " + (numConnections - successful.get()));
        System.out.println("Duration: " + duration + "ms");
        System.out.println("Rate: " + (numConnections * 1000 / duration) + " handshakes/sec");
        
        executor.shutdown();
    }
    
    private static void performHandshake(String host, int port, int id) throws IOException {
        try (Socket socket = new Socket(host, port)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            
            String request = "GET /gateway HTTP/1.1\r\n" +
                           "Host: " + host + "\r\n" +
                           "Upgrade: websocket\r\n" +
                           "Connection: Upgrade\r\n" +
                           "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                           "Sec-WebSocket-Version: 13\r\n\r\n";
            
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String statusLine = reader.readLine();
            
            if (statusLine != null && statusLine.contains("101")) {
                System.out.println("✓ Connection " + id + " upgraded successfully");
            } else {
                throw new IOException("Invalid response: " + statusLine);
            }
        }
    }
}
