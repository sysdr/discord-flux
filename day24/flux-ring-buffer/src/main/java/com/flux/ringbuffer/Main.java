package com.flux.ringbuffer;

import java.util.concurrent.TimeUnit;

/**
 * Main application demonstrating Ring Buffer backpressure handling.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Flux Ring Buffer Demo ===");
        System.out.println("Starting Gateway with Ring Buffers...\n");
        
        // Configuration
        int bufferCapacity = 256;  // Power of 2 for fast modulo
        int numClients = 100;
        int eventsPerSecond = 1000;
        int numSlowClients = 10;
        
        // Create Gateway
        Gateway gateway = new Gateway(bufferCapacity);
        
        // Create clients (90 fast, 10 slow)
        for (int i = 0; i < numClients; i++) {
            ClientConnection client = new ClientConnection("client-" + i, bufferCapacity);
            
            // Make some clients slow (simulate 3G network)
            if (i < numSlowClients) {
                client.setWriteDelayNanos(TimeUnit.MILLISECONDS.toNanos(100));
                System.out.println("Created SLOW client: " + client.getClientId());
            }
            
            gateway.addClient(client);
        }
        
        System.out.println("\nCreated " + numClients + " clients (" + numSlowClients + " slow)");
        
        // Start event generator
        EventGenerator generator = new EventGenerator(gateway, eventsPerSecond);
        generator.start();
        System.out.println("Event generator started (" + eventsPerSecond + " events/sec)");
        
        // Start dashboard
        Dashboard dashboard = new Dashboard(gateway, 8080);
        dashboard.start();
        
        System.out.println("\n✅ System running!");
        System.out.println("📊 Dashboard: http://localhost:8080");
        System.out.println("\nPress Ctrl+C to stop...\n");
        
        // Keep running
        Thread.currentThread().join();
    }
}
