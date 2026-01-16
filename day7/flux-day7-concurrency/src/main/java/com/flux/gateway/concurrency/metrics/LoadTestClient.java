package com.flux.gateway.concurrency.metrics;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTestClient {
    private final String host;
    private final int port;
    private final int numConnections;
    private final int messagesPerConnection;
    private final AtomicInteger successfulConnections = new AtomicInteger(0);
    private final AtomicInteger failedConnections = new AtomicInteger(0);
    
    public LoadTestClient(String host, int port, int numConnections, int messagesPerConnection) {
        this.host = host;
        this.port = port;
        this.numConnections = numConnections;
        this.messagesPerConnection = messagesPerConnection;
    }
    
    public void run() throws InterruptedException {
        System.out.println("Starting load test:");
        System.out.println("  Target: " + host + ":" + port);
        System.out.println("  Connections: " + numConnections);
        System.out.println("  Messages per connection: " + messagesPerConnection);
        
        CountDownLatch latch = new CountDownLatch(numConnections);
        long startTime = System.currentTimeMillis();
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numConnections; i++) {
                final int clientId = i;
                executor.submit(() -> {
                    try {
                        runClient(clientId);
                        successfulConnections.incrementAndGet();
                    } catch (Exception e) {
                        failedConnections.incrementAndGet();
                        if (failedConnections.get() < 10) {
                            System.err.println("Client " + clientId + " failed: " + e.getMessage());
                        }
                    } finally {
                        latch.countDown();
                    }
                });
                
                // Print progress
                if ((i + 1) % 1000 == 0) {
                    System.out.println("Spawned " + (i + 1) + " clients...");
                }
            }
            
            latch.await();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("\nLoad test completed:");
        System.out.println("  Duration: " + duration + "ms");
        System.out.println("  Successful: " + successfulConnections.get());
        System.out.println("  Failed: " + failedConnections.get());
        System.out.println("  Success rate: " + 
            (successfulConnections.get() * 100.0 / numConnections) + "%");
    }
    
    private void runClient(int clientId) throws IOException {
        try (Socket socket = new Socket(host, port)) {
            var out = socket.getOutputStream();
            var in = socket.getInputStream();
            
            byte[] message = ("Client " + clientId + " message\n").getBytes();
            byte[] response = new byte[1024];
            
            for (int i = 0; i < messagesPerConnection; i++) {
                out.write(message);
                out.flush();
                
                int bytesRead = in.read(response);
                if (bytesRead == -1) break;
                
                // Small delay to simulate real traffic
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: LoadTestClient <host> <port> <connections> <messages>");
            System.exit(1);
        }
        
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int connections = Integer.parseInt(args[2]);
        int messages = Integer.parseInt(args[3]);
        
        try {
            new LoadTestClient(host, port, connections, messages).run();
        } catch (InterruptedException e) {
            System.err.println("Load test interrupted");
        }
    }
}
