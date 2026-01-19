package com.flux.netpoll;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SimpleTestClient {
    public static void main(String[] args) throws Exception {
        int connections = Integer.parseInt(args.length > 0 ? args[0] : "100");
        
        System.out.println("🚀 Creating " + connections + " persistent connections...");
        
        SocketChannel[] channels = new SocketChannel[connections];
        
        // Create and keep connections alive
        for (int i = 0; i < connections; i++) {
            try {
                SocketChannel channel = SocketChannel.open();
                channel.configureBlocking(true);
                channel.connect(new InetSocketAddress("localhost", 9090));
                
                // Send initial message
                ByteBuffer buffer = ByteBuffer.wrap(("Hello-" + i + "\n").getBytes());
                channel.write(buffer);
                
                // Read response
                buffer = ByteBuffer.allocate(1024);
                channel.read(buffer);
                
                channels[i] = channel;
                
                if ((i + 1) % 10 == 0) {
                    System.out.println("✓ Created " + (i + 1) + " connections");
                }
            } catch (Exception e) {
                System.err.println("Failed to create connection " + i + ": " + e.getMessage());
            }
        }
        
        System.out.println("✓ All connections established. Keeping them alive for 60 seconds...");
        System.out.println("📊 Check dashboard at http://localhost:8080/dashboard");
        
        // Keep connections alive
        Thread.sleep(60000);
        
        // Close all connections
        System.out.println("Closing connections...");
        for (SocketChannel channel : channels) {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        }
        
        System.out.println("✓ Done");
    }
}
