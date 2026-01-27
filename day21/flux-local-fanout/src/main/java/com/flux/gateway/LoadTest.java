package com.flux.gateway;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class LoadTest {
    
    public static void main(String[] args) throws Exception {
        int numConnections = 100;
        String host = "localhost";
        int port = 9001;
        
        if (args.length >= 1) {
            numConnections = Integer.parseInt(args[0]);
        }
        
        System.out.println("========================================");
        System.out.println("FLUX LOAD TEST - Spawning Connections");
        System.out.println("========================================");
        System.out.println("Target: " + host + ":" + port);
        System.out.println("Connections: " + numConnections);
        
        List<SocketChannel> connections = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(numConnections);
        
        long startTime = System.currentTimeMillis();
        
        // Spawn connections using Virtual Threads
        for (int i = 0; i < numConnections; i++) {
            final int index = i;
            Thread.startVirtualThread(() -> {
                try {
                    SocketChannel channel = SocketChannel.open();
                    channel.connect(new InetSocketAddress(host, port));
                    
                    // Send a simple handshake
                    ByteBuffer hello = ByteBuffer.wrap("HELLO".getBytes());
                    channel.write(hello);
                    
                    synchronized (connections) {
                        connections.add(channel);
                    }
                    
                    if ((index + 1) % 10 == 0) {
                        System.out.println("[PROGRESS] " + (index + 1) + " connections established");
                    }
                    
                } catch (Exception e) {
                    System.err.println("[ERROR] Failed to connect #" + index + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("\n========================================");
        System.out.println("LOAD TEST COMPLETE");
        System.out.println("========================================");
        System.out.println("Successful connections: " + connections.size() + "/" + numConnections);
        System.out.println("Time taken: " + duration + "ms");
        System.out.println("Rate: " + (numConnections * 1000 / duration) + " conn/sec");
        System.out.println("\nConnections are active. Press Ctrl+C to close them.");
        
        // Keep connections alive
        Thread.sleep(Long.MAX_VALUE);
    }
}
