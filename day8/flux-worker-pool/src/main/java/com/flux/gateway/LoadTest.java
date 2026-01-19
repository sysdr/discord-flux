package com.flux.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Load test using Virtual Threads to simulate thousands of clients.
 */
public class LoadTest {
    public static void main(String[] args) throws InterruptedException {
        int numClients = 1000;
        int messagesPerClient = 100;
        
        System.out.println("🔥 Starting load test: " + numClients + " clients");
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            executor.submit(() -> runClient(clientId, messagesPerClient));
        }
        
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        
        System.out.println("✅ Load test completed");
    }

    private static void runClient(int clientId, int messageCount) {
        try (Socket socket = new Socket("localhost", 8080)) {
            OutputStream out = socket.getOutputStream();
            
            for (int i = 0; i < messageCount; i++) {
                String message = "CLIENT" + clientId + "_MSG" + i;
                out.write(message.getBytes());
                out.flush();
                
                // Simulate realistic message rate
                Thread.sleep(10);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Client " + clientId + " error: " + e.getMessage());
        }
    }
}
