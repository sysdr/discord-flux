package com.flux;

import com.flux.compaction.*;
import com.flux.model.Message;
import com.flux.server.DashboardServer;
import com.flux.storage.StorageEngine;

import java.nio.file.Paths;

public class FluxStorageEngine {
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Starting Flux Storage Engine...");
        
        // Start dashboard
        DashboardServer dashboard = new DashboardServer(8080);
        dashboard.start();
        
        System.out.println("✓ Dashboard running at http://localhost:8080");
        System.out.println("✓ Storage engine ready");
        System.out.println("\nPress Ctrl+C to stop");
        
        // Keep alive
        Thread.currentThread().join();
    }
}
