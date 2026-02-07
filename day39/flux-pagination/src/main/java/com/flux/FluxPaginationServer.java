package com.flux;

import com.flux.http.HttpServer;
import com.flux.pagination.CassandraClient;

public class FluxPaginationServer {
    
    public static void main(String[] args) {
        System.out.println("🚀 Starting Flux Pagination Server...");
        
        String cassandraHost = System.getenv().getOrDefault("CASSANDRA_HOST", "localhost");
        int cassandraPort = Integer.parseInt(System.getenv().getOrDefault("CASSANDRA_PORT", "9042"));
        int httpPort = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
        
        try (CassandraClient cassandraClient = new CassandraClient(cassandraHost, cassandraPort)) {
            cassandraClient.initializeSchema();
            
            HttpServer httpServer = new HttpServer(httpPort, cassandraClient);
            httpServer.start();
            
            System.out.println("✅ Server ready. Access dashboard at http://localhost:" + httpPort + "/dashboard.html");
            System.out.println("Press Ctrl+C to stop...");
            
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("❌ Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
