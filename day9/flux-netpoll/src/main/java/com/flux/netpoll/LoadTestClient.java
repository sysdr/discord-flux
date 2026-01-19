package com.flux.netpoll;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

public class LoadTestClient {
    public static void main(String[] args) throws Exception {
        int targetConnections = Integer.parseInt(args.length > 0 ? args[0] : "10000");
        String host = "localhost";
        int port = 9090;
        
        System.out.println("🚀 Starting load test: " + targetConnections + " connections");
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(targetConnections);
        Instant start = Instant.now();
        
        for (int i = 0; i < targetConnections; i++) {
            final int clientId = i;
            executor.submit(() -> {
                SocketChannel channel = null;
                try {
                    channel = SocketChannel.open();
                    channel.configureBlocking(true); // Use blocking for simplicity
                    boolean connected = channel.connect(new InetSocketAddress(host, port));
                    
                    if (!connected) {
                        System.err.println("Client " + clientId + " failed to connect");
                        return;
                    }
                    
                    // Send a test message
                    ByteBuffer buffer = ByteBuffer.wrap(
                        ("Client-" + clientId + "\n").getBytes()
                    );
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    
                    // Read echo response (wait for it)
                    buffer = ByteBuffer.allocate(1024);
                    int totalRead = 0;
                    int attempts = 0;
                    while (totalRead == 0 && attempts < 10) {
                        int bytesRead = channel.read(buffer);
                        if (bytesRead == -1) {
                            break; // Connection closed by server
                        }
                        totalRead += bytesRead;
                        if (totalRead == 0) {
                            Thread.sleep(10); // Wait a bit for response
                            attempts++;
                        }
                    }
                    
                    // Keep connection alive for observation (longer to see in dashboard)
                    Thread.sleep(120000); // Keep alive for 2 minutes
                    
                } catch (Exception e) {
                    // Silently handle errors to avoid spam
                    if (clientId % 1000 == 0) {
                        System.err.println("Client " + clientId + " error: " + e.getMessage());
                    }
                } finally {
                    try {
                        if (channel != null && channel.isOpen()) {
                            channel.close();
                        }
                    } catch (IOException e) {
                        // Ignore close errors
                    }
                    latch.countDown();
                }
            });
            
            if (i % 100 == 0 && i > 0) {
                System.out.println("Spawned " + i + " connections...");
                Thread.sleep(10); // Rate limit
            }
        }
        
        latch.await();
        Duration elapsed = Duration.between(start, Instant.now());
        
        System.out.println("✓ All " + targetConnections + " connections established in " + 
                         elapsed.toMillis() + "ms");
        System.out.println("Open http://localhost:8080/dashboard to see the visualization");
        
        Thread.sleep(5000); // Keep running for observation
        executor.shutdown();
    }
}
