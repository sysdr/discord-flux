package com.flux.netpoll;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Test client specifically designed to keep connections alive
 * and visible in the dashboard connection grid
 */
public class GridTestClient {
    public static void main(String[] args) throws Exception {
        int connections = Integer.parseInt(args.length > 0 ? args[0] : "50");
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   FLUX NETPOLL - GRID TEST CLIENT     ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println("");
        System.out.println("🚀 Creating " + connections + " persistent connections...");
        System.out.println("📊 Dashboard: http://localhost:8080/dashboard");
        System.out.println("   Watch the connection grid fill up!");
        System.out.println("");
        
        List<SocketChannel> channels = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        
        // Create connections with retry logic
        for (int i = 0; i < connections; i++) {
            SocketChannel channel = null;
            try {
                channel = SocketChannel.open();
                channel.configureBlocking(true);
                
                // Connect with timeout
                boolean connected = channel.connect(new InetSocketAddress("localhost", 9090));
                
                if (!connected) {
                    System.err.println("❌ Connection " + i + " failed to connect");
                    failedCount++;
                    continue;
                }
                
                // Send initial message
                ByteBuffer buffer = ByteBuffer.wrap(("GridTest-" + i + "\n").getBytes());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                
                // Wait for server to process
                Thread.sleep(20);
                
                // Read response
                buffer = ByteBuffer.allocate(1024);
                int bytesRead = channel.read(buffer);
                
                if (bytesRead > 0 || channel.isConnected()) {
                    channels.add(channel);
                    successCount++;
                    
                    if (successCount % 10 == 0) {
                        System.out.println("✓ Created " + successCount + " active connections");
                    }
                } else {
                    channel.close();
                    failedCount++;
                }
                
            } catch (Exception e) {
                failedCount++;
                if (channel != null) {
                    try {
                        channel.close();
                    } catch (IOException ignored) {}
                }
                if (i % 10 == 0) {
                    System.err.println("⚠ Connection " + i + " error: " + e.getMessage());
                }
            }
            
            // Small delay to avoid overwhelming
            if (i % 5 == 0 && i > 0) {
                Thread.sleep(10);
            }
        }
        
        System.out.println("");
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║         CONNECTION SUMMARY              ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println("✓ Successful: " + successCount);
        System.out.println("✗ Failed: " + failedCount);
        System.out.println("📊 Total active: " + channels.size());
        System.out.println("");
        System.out.println("🔗 Keeping " + channels.size() + " connections alive for 3 minutes...");
        System.out.println("📊 Check dashboard: http://localhost:8080/dashboard");
        System.out.println("   You should see " + channels.size() + " green boxes in the grid!");
        System.out.println("");
        
        // Keep connections alive
        long startTime = System.currentTimeMillis();
        long duration = 3 * 60 * 1000; // 3 minutes
        
        while (System.currentTimeMillis() - startTime < duration) {
            // Remove closed channels
            channels.removeIf(ch -> {
                try {
                    return !ch.isOpen() || !ch.isConnected();
                } catch (Exception e) {
                    return true;
                }
            });
            
            // Send periodic keep-alive pings
            if ((System.currentTimeMillis() - startTime) % 10000 < 1000) {
                for (SocketChannel channel : channels) {
                    try {
                        if (channel.isOpen() && channel.isConnected()) {
                            ByteBuffer ping = ByteBuffer.wrap("ping\n".getBytes());
                            channel.write(ping);
                        }
                    } catch (IOException e) {
                        // Channel closed, will be removed in next iteration
                    }
                }
                System.out.println("💓 Keep-alive ping sent to " + channels.size() + " connections");
            }
            
            Thread.sleep(1000);
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
        
        System.out.println("✓ All connections closed");
        System.out.println("✓ Test complete");
    }
}
