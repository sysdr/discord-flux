package com.flux.netpoll;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Active demo client that keeps connections alive and sends periodic messages
 * to generate events and show virtual threads in action
 */
public class ActiveDemoClient {
    public static void main(String[] args) throws Exception {
        int connections = Integer.parseInt(args.length > 0 ? args[0] : "20");
        int durationMinutes = Integer.parseInt(args.length > 1 ? args[1] : "5");
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   FLUX NETPOLL - ACTIVE DEMO CLIENT   ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println("");
        System.out.println("🚀 Creating " + connections + " active connections...");
        System.out.println("📊 Dashboard: http://localhost:8080/dashboard");
        System.out.println("⏱️  Duration: " + durationMinutes + " minutes");
        System.out.println("");
        
        List<SocketChannel> channels = new ArrayList<>();
        int successCount = 0;
        
        // Create connections
        for (int i = 0; i < connections; i++) {
            try {
                SocketChannel channel = SocketChannel.open();
                channel.configureBlocking(true);
                
                boolean connected = channel.connect(new InetSocketAddress("localhost", 9090));
                if (!connected) {
                    channel.close();
                    continue;
                }
                
                // Send initial message
                ByteBuffer buffer = ByteBuffer.wrap(("DemoClient-" + i + "\n").getBytes());
                channel.write(buffer);
                
                // Read response
                buffer = ByteBuffer.allocate(1024);
                channel.read(buffer);
                
                channels.add(channel);
                successCount++;
                
                if (successCount % 5 == 0) {
                    System.out.println("✓ Created " + successCount + " connections");
                }
                
                Thread.sleep(50); // Small delay
            } catch (Exception e) {
                if (i % 10 == 0) {
                    System.err.println("⚠ Connection " + i + " error: " + e.getMessage());
                }
            }
        }
        
        System.out.println("");
        System.out.println("✅ " + successCount + " connections established!");
        System.out.println("📊 Check dashboard: http://localhost:8080/dashboard");
        System.out.println("   You should see:");
        System.out.println("   - Events Processed: > 0");
        System.out.println("   - Active Connections: " + successCount);
        System.out.println("   - Virtual Threads: may show briefly during activity");
        System.out.println("");
        System.out.println("🔄 Sending periodic messages to generate events...");
        System.out.println("");
        
        // Keep connections alive and send periodic messages
        long startTime = System.currentTimeMillis();
        long duration = durationMinutes * 60 * 1000;
        int messageCount = 0;
        
        while (System.currentTimeMillis() - startTime < duration) {
            // Remove closed channels
            channels.removeIf(ch -> {
                try {
                    return !ch.isOpen() || !ch.isConnected();
                } catch (Exception e) {
                    return true;
                }
            });
            
            // Send periodic messages to generate events (every 2 seconds)
            if ((System.currentTimeMillis() - startTime) % 2000 < 100) {
                for (SocketChannel channel : channels) {
                    try {
                        if (channel.isOpen() && channel.isConnected()) {
                            ByteBuffer msg = ByteBuffer.wrap(("ping-" + messageCount + "\n").getBytes());
                            channel.write(msg);
                            
                            // Read response (this will trigger virtual thread)
                            ByteBuffer response = ByteBuffer.allocate(1024);
                            channel.read(response);
                            
                            messageCount++;
                        }
                    } catch (IOException e) {
                        // Channel closed, will be removed
                    }
                }
                
                if (messageCount % 10 == 0) {
                    System.out.println("💓 Sent " + messageCount + " messages (" + channels.size() + " active connections)");
                }
            }
            
            Thread.sleep(100);
        }
        
        // Close all connections
        System.out.println("");
        System.out.println("🔌 Closing all connections...");
        for (SocketChannel channel : channels) {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
        
        System.out.println("✅ Demo complete!");
        System.out.println("   Total messages sent: " + messageCount);
    }
}
