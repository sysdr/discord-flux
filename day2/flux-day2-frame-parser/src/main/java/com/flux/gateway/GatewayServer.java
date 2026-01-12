package com.flux.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executors;

/**
 * Main Gateway server using Virtual Threads and NIO.
 * One virtual thread per connection (blocking I/O model).
 */
public class GatewayServer {
    
    private final int port;
    private final MetricsCollector metrics;
    private volatile boolean running;

    public GatewayServer(int port) {
        this.port = port;
        this.metrics = new MetricsCollector();
    }

    public void start() throws IOException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             var serverChannel = ServerSocketChannel.open()) {
            
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(true); // Blocking accept
            running = true;

            System.out.println("🚀 Flux Gateway Server started on port " + port);
            System.out.println("📊 Dashboard available at http://localhost:8080");
            System.out.println("⚡ Using Virtual Threads for connection handling");

            // Start dashboard server
            executor.submit(() -> new DashboardServer(8080, metrics).start());

            // Accept loop
            while (running) {
                SocketChannel clientChannel = serverChannel.accept();
                clientChannel.configureBlocking(true);
                
                // Spawn virtual thread for this connection
                ConnectionHandler handler = new ConnectionHandler(clientChannel, metrics);
                executor.submit(handler);
            }
        }
    }

    public void stop() {
        running = false;
    }

    public MetricsCollector getMetrics() {
        return metrics;
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9001;
        GatewayServer server = new GatewayServer(port);
        
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.exit(1);
        }
    }
}
