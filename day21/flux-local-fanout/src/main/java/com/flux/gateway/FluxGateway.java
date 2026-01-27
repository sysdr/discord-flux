package com.flux.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class FluxGateway {
    private final int port;
    private final LocalConnectionRegistry registry;
    private final BroadcastEngine broadcastEngine;
    private final RedisSubscriber redisSubscriber;
    private final DashboardServer dashboardServer;
    private volatile boolean running;
    
    public FluxGateway(int port, int dashboardPort) {
        this.port = port;
        this.registry = new LocalConnectionRegistry();
        this.broadcastEngine = new BroadcastEngine(registry);
        this.redisSubscriber = new RedisSubscriber(
            "localhost", 6379, "guild_events", broadcastEngine
        );
        this.dashboardServer = new DashboardServer(dashboardPort, registry, broadcastEngine);
        this.running = false;
    }
    
    public void start() throws IOException {
        System.out.println("========================================");
        System.out.println("FLUX GATEWAY - Local Fan-Out");
        System.out.println("========================================");
        
        // Start dashboard
        dashboardServer.start();
        
        // Start Redis subscriber
        redisSubscriber.start();
        
        // Start NIO gateway
        running = true;
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        
        Selector selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("[GATEWAY] Listening on port: " + port);
        System.out.println("[GATEWAY] Press Ctrl+C to shutdown");
        
        while (running) {
            selector.select(1000); // 1 second timeout
            
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                
                try {
                    if (key.isAcceptable()) {
                        handleAccept(serverChannel, selector);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                } catch (Exception e) {
                    System.err.println("[GATEWAY] Error handling key: " + e.getMessage());
                    handleDisconnect(key);
                }
            }
            
            // Periodically evict slow consumers
            if (System.currentTimeMillis() % 10000 < 1000) {
                int evicted = registry.evictSlowConsumers();
                if (evicted > 0) {
                    System.out.println("[GATEWAY] Evicted " + evicted + " slow consumers");
                }
            }
        }
    }
    
    private void handleAccept(ServerSocketChannel serverChannel, Selector selector) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        
        SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ);
        SessionId sessionId = SessionId.generate();
        
        GatewayConnection connection = new GatewayConnection(sessionId, clientChannel, key);
        key.attach(connection);
        
        // Auto-subscribe to guild_001 for demo purposes
        connection.subscribeToGuild(new GuildId("guild_001"));
        
        registry.register(sessionId, connection);
    }
    
    private void handleRead(SelectionKey key) throws IOException {
        GatewayConnection connection = (GatewayConnection) key.attachment();
        SocketChannel channel = connection.socketChannel();
        
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = channel.read(buffer);
        
        if (bytesRead == -1) {
            handleDisconnect(key);
            return;
        }
        
        // In a real implementation, we'd parse WebSocket frames here
        // For now, just acknowledge receipt
        System.out.println("[GATEWAY] Received " + bytesRead + " bytes from " + connection.sessionId());
    }
    
    private void handleWrite(SelectionKey key) {
        GatewayConnection connection = (GatewayConnection) key.attachment();
        
        try {
            int drained = connection.drainWriteQueue();
            if (drained > 0) {
                System.out.println("[GATEWAY] Drained " + drained + " messages for " + connection.sessionId());
            }
        } catch (Exception e) {
            System.err.println("[GATEWAY] Write error: " + e.getMessage());
            handleDisconnect(key);
        }
    }
    
    private void handleDisconnect(SelectionKey key) {
        GatewayConnection connection = (GatewayConnection) key.attachment();
        if (connection != null) {
            registry.unregister(connection.sessionId());
        }
        key.cancel();
    }
    
    public void stop() {
        running = false;
        redisSubscriber.stop();
        dashboardServer.stop();
    }
    
    public static void main(String[] args) throws IOException {
        int gatewayPort = 9001;
        int dashboardPort = 8080;
        
        if (args.length >= 1) {
            gatewayPort = Integer.parseInt(args[0]);
        }
        if (args.length >= 2) {
            dashboardPort = Integer.parseInt(args[1]);
        }
        
        FluxGateway gateway = new FluxGateway(gatewayPort, dashboardPort);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SHUTDOWN] Stopping gateway...");
            gateway.stop();
        }));
        
        gateway.start();
    }
}
