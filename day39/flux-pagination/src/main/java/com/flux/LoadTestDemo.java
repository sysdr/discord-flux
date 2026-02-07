package com.flux;

import com.flux.pagination.CassandraClient;

public class LoadTestDemo {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: LoadTestDemo <channelId> <messageCount>");
            System.exit(1);
        }
        
        long channelId = Long.parseLong(args[0]);
        int messageCount = Integer.parseInt(args[1]);
        
        System.out.println("🚀 Load Test Demo");
        System.out.println("Channel ID: " + channelId);
        System.out.println("Message Count: " + messageCount);
        
        try (CassandraClient client = new CassandraClient("localhost", 9042)) {
            client.initializeSchema();
            
            long startTime = System.currentTimeMillis();
            client.bulkInsert(channelId, messageCount);
            long duration = System.currentTimeMillis() - startTime;
            
            double throughput = (messageCount / (duration / 1000.0));
            
            System.out.println("✅ Load test complete");
            System.out.println("Duration: " + duration + " ms");
            System.out.println("Throughput: " + String.format("%.0f", throughput) + " inserts/sec");
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
