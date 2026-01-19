package com.flux.netpoll;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class PersistentClient {
    public static void main(String[] args) throws Exception {
        int connections = Integer.parseInt(args.length > 0 ? args[0] : "50");
        
        System.out.println("🚀 Creating " + connections + " persistent connections...");
        System.out.println("📊 Dashboard: http://localhost:8080/dashboard");
        System.out.println("   Connections will stay alive for 5 minutes");
        System.out.println("");
        
        List<SocketChannel> channels = new ArrayList<>();
        int successCount = 0;
        
        // Create connections
        for (int i = 0; i < connections; i++) {
            try {
                SocketChannel channel = SocketChannel.open();
                channel.configureBlocking(true);
                
                // Connect with timeout simulation
                long start = System.currentTimeMillis();
                boolean connected = channel.connect(new InetSocketAddress("localhost", 9090));
                
                if (!connected) {
                    System.err.println("Connection " + i + " failed to connect");
                    continue;
                }
                
                // Send initial message
                ByteBuffer buffer = ByteBuffer.wrap(("Client-" + i + "\n").getBytes());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                
                // Wait a bit for server to process
                Thread.sleep(50);
                
                // Read response (non-blocking check)
                buffer = ByteBuffer.allocate(1024);
                int bytesRead = channel.read(buffer);
                
                if (bytesRead > 0) {
                    channels.add(channel);
                    successCount++;
                    
                    if (successCount % 10 == 0) {
                        System.out.println("✓ Created " + successCount + " active connections");
                    }
                } else {
                    channel.close();
                    System.err.println("Connection " + i + " failed to get response");
                }
                
            } catch (Exception e) {
                System.err.println("Failed connection " + i + ": " + e.getMessage());
            }
            
            // Small delay to avoid overwhelming the server
            if (i % 10 == 0 && i > 0) {
                Thread.sleep(100);
            }
        }
        
        System.out.println("");
        System.out.println("✓ Total active connections: " + successCount);
        System.out.println("✓ Keeping connections alive for 5 minutes...");
        System.out.println("📊 Check dashboard: http://localhost:8080/dashboard");
        System.out.println("");
        
        // Keep connections alive
        long startTime = System.currentTimeMillis();
        long duration = 5 * 60 * 1000; // 5 minutes
        
        while (System.currentTimeMillis() - startTime < duration) {
            // Periodically send keep-alive messages
            for (int i = 0; i < channels.size(); i++) {
                SocketChannel channel = channels.get(i);
                try {
                    if (channel.isOpen() && channel.isConnected()) {
                        // Send a keep-alive ping every 30 seconds
                        if ((System.currentTimeMillis() - startTime) % 30000 < 1000) {
                            ByteBuffer ping = ByteBuffer.wrap("ping\n".getBytes());
                            channel.write(ping);
                        }
                    } else {
                        channels.remove(i);
                        i--;
                    }
                } catch (IOException e) {
                    channels.remove(i);
                    i--;
                }
            }
            Thread.sleep(1000);
        }
        
        // Close all connections
        System.out.println("Closing all connections...");
        for (SocketChannel channel : channels) {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
        
        System.out.println("✓ Done");
    }
}
