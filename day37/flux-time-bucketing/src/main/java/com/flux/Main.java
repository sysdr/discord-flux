package com.flux;

import com.flux.bucketing.PartitionKeyGenerator;
import com.flux.dashboard.DashboardServer;

import java.io.IOException;

/**
 * Main entry point for the Flux Time Bucketing demonstration.
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   FLUX TIME BUCKETING - Day 37");
        System.out.println("=================================================");
        System.out.println();
        
        // Display configuration
        System.out.printf("Bucket Size: %d days%n", 
            PartitionKeyGenerator.getBucketSizeMs() / (24 * 60 * 60 * 1000));
        System.out.printf("Epoch Start: %s%n", 
            java.time.Instant.ofEpochMilli(PartitionKeyGenerator.getEpochStart()));
        System.out.println();
        
        // Start dashboard
        try {
            DashboardServer server = new DashboardServer(8080);
            server.start();
            
            System.out.println("✓ Dashboard started on http://localhost:8080");
            System.out.println();
            System.out.println("Press Ctrl+C to stop...");
            
            // Keep alive
            Thread.currentThread().join();
        } catch (IOException e) {
            System.err.println("Failed to start dashboard: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            System.out.println("\nShutting down...");
        }
    }
}
