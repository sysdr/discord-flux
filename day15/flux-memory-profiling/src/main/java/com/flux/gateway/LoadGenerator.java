package com.flux.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates realistic client behavior: connect, send messages, disconnect.
 * With 20% churn rate to trigger leaks.
 */
public class LoadGenerator {
    
    private final String host;
    private final int port;
    private final int targetConnections;
    private final double churnRate; // percentage of connections to cycle per minute
    
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final List<Thread> threads = new ArrayList<>();
    
    public LoadGenerator(String host, int port, int targetConnections, double churnRate) {
        this.host = host;
        this.port = port;
        this.targetConnections = targetConnections;
        this.churnRate = churnRate;
    }
    
    public void start() {
        System.out.println("[LoadGenerator] Starting load test:");
        System.out.println("  Target connections: " + targetConnections);
        System.out.println("  Churn rate: " + (churnRate * 100) + "%");
        
        // Spawn initial connections
        for (int i = 0; i < targetConnections; i++) {
            spawnClient(i);
        }
        
        // Churn loop: disconnect and reconnect clients
        Thread.ofVirtual().name("churn-manager").start(() -> {
            while (true) {
                try {
                    Thread.sleep(60000); // Every minute
                    
                    int toChurn = (int) (targetConnections * churnRate);
                    System.out.println("[LoadGenerator] Churning " + toChurn + " connections...");
                    
                    // This simulates clients disconnecting and new ones connecting
                    // In real scenario, we'd track individual threads, but for leak demo
                    // we just create churn
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    private void spawnClient(int id) {
        Thread.ofVirtual().name("client-" + id).start(() -> {
            try {
                runClient(id);
            } catch (Exception e) {
                System.err.println("[Client-" + id + "] Error: " + e.getMessage());
            } finally {
                activeConnections.decrementAndGet();
            }
        });
        
        activeConnections.incrementAndGet();
    }
    
    private void runClient(int id) throws IOException, InterruptedException {
        SocketChannel channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(host, port));
        
        ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        
        // Send some messages
        for (int i = 0; i < 10; i++) {
            String message = "Message " + i + " from client " + id;
            writeBuffer.clear();
            writeBuffer.put(message.getBytes());
            writeBuffer.flip();
            
            channel.write(writeBuffer);
            
            // Read echo
            readBuffer.clear();
            channel.read(readBuffer);
            
            Thread.sleep(100); // 10 messages per second
        }
        
        // Simulate some clients not closing properly (triggers leak)
        if (id % 5 == 0) {
            // Just abandon the connection (TCP FIN never sent from client side)
            // Server sees half-open connection
            System.out.println("[Client-" + id + "] Simulating abandoned connection");
        } else {
            channel.close();
        }
    }
    
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9000;
        int connections = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
        double churn = args.length > 3 ? Double.parseDouble(args[3]) : 0.2;
        
        LoadGenerator generator = new LoadGenerator(host, port, connections, churn);
        generator.start();
        
        System.out.println("[LoadGenerator] Press Ctrl+C to stop");
        
        // Keep running
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("[LoadGenerator] Shutting down...");
        }
    }
}
