package com.flux.tombstone;

import java.io.IOException;

public class FluxTombstoneServer {
    
    public static void main(String[] args) throws IOException {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  Flux Day 40: Tombstone Deletion System             ║");
        System.out.println("║  LSM Trees - Why We Don't DELETE Immediately        ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();
        
        MessageStore store = new MessageStore();
        
        // Pre-populate with some data
        System.out.println("📝 Pre-populating with sample data...");
        for (int i = 0; i < 500; i++) {
            Message msg = new Message("general", "Sample message " + i);
            store.insert(msg);
        }
        
        DashboardServer dashboard = new DashboardServer(store, 8080);
        dashboard.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down...");
            dashboard.stop();
            store.shutdown();
        }));
        
        System.out.println("✅ Server ready!");
        System.out.println("   Press Ctrl+C to stop");
    }
}
