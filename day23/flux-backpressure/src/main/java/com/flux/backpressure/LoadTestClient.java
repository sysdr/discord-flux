package com.flux.backpressure;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Load test client that spawns multiple connections using Virtual Threads.
 * Some clients can be configured as "slow" (not reading from socket).
 */
public class LoadTestClient {
    private final String host;
    private final int port;
    private final int numClients;
    private final int numSlowClients;
    
    public LoadTestClient(String host, int port, int numClients, int numSlowClients) {
        this.host = host;
        this.port = port;
        this.numClients = numClients;
        this.numSlowClients = numSlowClients;
    }
    
    public void run() throws InterruptedException {
        List<Thread> clientThreads = new ArrayList<>();
        
        System.out.println("[LoadTest] Spawning " + numClients + " clients (" + 
                          numSlowClients + " slow)...");
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            final boolean isSlow = i < numSlowClients;
            
            Thread clientThread = Thread.ofVirtual().start(() -> {
                runClient(clientId, isSlow);
            });
            
            clientThreads.add(clientThread);
            Thread.sleep(10); // Stagger connections
        }
        
        System.out.println("[LoadTest] All clients connected. Press Ctrl+C to stop.");
        
        // Wait for all clients
        for (Thread thread : clientThreads) {
            thread.join();
        }
    }
    
    private void runClient(int clientId, boolean isSlow) {
        try (SocketChannel channel = SocketChannel.open()) {
            channel.connect(new InetSocketAddress(host, port));
            channel.configureBlocking(true);
            
            System.out.println("[Client:" + clientId + "] Connected" + 
                              (isSlow ? " (SLOW MODE)" : ""));
            
            ByteBuffer readBuffer = ByteBuffer.allocate(1024);
            
            while (true) {
                if (isSlow) {
                    // Slow client: never read (fills TCP send buffer on server side)
                    // Sleep indefinitely to block and not read
                    Thread.sleep(Long.MAX_VALUE);
                } else {
                    // Fast client: read messages normally
                    readBuffer.clear();
                    int bytesRead = channel.read(readBuffer);
                    if (bytesRead == -1) {
                        break; // Server closed connection
                    }
                }
            }
            
        } catch (IOException | InterruptedException e) {
            if (!isSlow) {
                System.out.println("[Client:" + clientId + "] Disconnected: " + e.getMessage());
            } else {
                System.out.println("[Client:" + clientId + "] EVICTED (slow consumer)");
            }
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        int numClients = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int numSlowClients = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        
        LoadTestClient loadTest = new LoadTestClient("localhost", 9090, numClients, numSlowClients);
        loadTest.run();
    }
}
