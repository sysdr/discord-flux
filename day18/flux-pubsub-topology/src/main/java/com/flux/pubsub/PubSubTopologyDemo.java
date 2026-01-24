package com.flux.pubsub;

import java.io.IOException;

/**
 * Main entry point for the PubSub Topology demonstration.
 */
public class PubSubTopologyDemo {
    public static void main(String[] args) throws IOException {
        System.out.println("🚀 Starting Flux PubSub Topology Demo...");
        
        var broker = new LocalPubSubBroker();
        
        // Set up some sample guilds
        setupSampleTopology(broker);
        
        // Start dashboard
        var dashboard = new DashboardServer(8080, broker);
        dashboard.start();
        
        System.out.println("✓ Broker initialized with " + broker.topicCount() + " topics");
        System.out.println("✓ Dashboard available at http://localhost:8080/");
        System.out.println("\nPress Ctrl+C to stop...");
        
        // Keep running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("\n🛑 Shutting down...");
            dashboard.stop();
        }
    }
    
    private static void setupSampleTopology(LocalPubSubBroker broker) {
        // Create 10 sample guilds with varying member counts
        for (int guild = 0; guild < 10; guild++) {
            String guildTopic = "guild:" + guild;
            int memberCount = 50 + (guild * 50); // 50-500 members
            
            for (int member = 0; member < memberCount; member++) {
                var subscriber = new GatewaySubscriber(
                    "user_" + guild + "_" + member,
                    1024,
                    null
                );
                broker.subscribe(guildTopic, subscriber);
            }
        }
    }
}
