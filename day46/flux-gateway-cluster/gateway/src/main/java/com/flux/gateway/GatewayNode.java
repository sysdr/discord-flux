package com.flux.gateway;

import com.flux.gateway.websocket.WebSocketHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class GatewayNode {
    private final String nodeId;
    private final int websocketPort;
    private final int healthPort;
    private final ConnectionManager connectionManager;
    private final RegistrationService registrationService;
    private final WebSocketHandler webSocketHandler;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private final Map<SocketChannel, StringBuilder> requestBuffers = new ConcurrentHashMap<>();
    private final Map<SocketChannel, String> connectionIds = new ConcurrentHashMap<>();
    
    public GatewayNode(String nodeId, String redisHost, int redisPort, 
                       int websocketPort, int healthPort) throws IOException {
        this.nodeId = nodeId;
        this.websocketPort = websocketPort;
        this.healthPort = healthPort;
        this.connectionManager = new ConnectionManager();
        this.webSocketHandler = new WebSocketHandler(connectionManager);
        
        // Get container IP (Docker uses hostname as IP identifier)
        var hostAddress = InetAddress.getLocalHost().getHostAddress();
        
        this.registrationService = new RegistrationService(
            redisHost, redisPort, nodeId, hostAddress, websocketPort, connectionManager
        );
    }
    
    public void start() throws IOException {
        System.out.println("=".repeat(60));
        System.out.println("🚀 Starting Flux Gateway Node: " + nodeId);
        System.out.println("=".repeat(60));
        
        // Setup shutdown hook
        setupShutdownHook();
        
        // Start health check server
        startHealthServer();
        
        // Start WebSocket server
        startWebSocketServer();
        
        // Register with service registry
        registrationService.register();
        
        System.out.println("✓ Gateway started successfully");
        System.out.println("  - WebSocket: 0.0.0.0:" + websocketPort);
        System.out.println("  - Health Check: 0.0.0.0:" + healthPort);
        System.out.println("=".repeat(60));
        
        // Main event loop
        runEventLoop();
    }
    
    private void startHealthServer() throws IOException {
        var healthServer = HttpServer.create(new InetSocketAddress(healthPort), 0);
        
        healthServer.createContext("/health", exchange -> {
            var status = isShuttingDown.get() ? "DRAINING" : "HEALTHY";
            var responseCode = isShuttingDown.get() ? 503 : 200;
            var response = "{\"status\":\"" + status + "\",\"connections\":" + 
                          connectionManager.getActiveConnectionCount() + "}";
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseCode, response.length());
            
            try (var os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });
        
        // Use virtual threads for non-blocking health checks
        healthServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        healthServer.start();
        
        System.out.println("[Health] Health server started on port " + healthPort);
    }
    
    private void startWebSocketServer() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(websocketPort));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("[WebSocket] Server listening on port " + websocketPort);
    }
    
    private void runEventLoop() {
        try {
            while (!isShuttingDown.get()) {
                selector.select(1000); // 1 second timeout
                
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    var key = keys.next();
                    keys.remove();
                    
                    if (!key.isValid()) continue;
                    
                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        }
                    } catch (Exception e) {
                        System.err.println("[EventLoop] Error handling key: " + e.getMessage());
                        key.cancel();
                        try {
                            key.channel().close();
                        } catch (IOException ignored) {}
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[EventLoop] Fatal error: " + e.getMessage());
        }
    }
    
    private void handleAccept(SelectionKey key) throws IOException {
        var serverChannel = (ServerSocketChannel) key.channel();
        var clientChannel = serverChannel.accept();
        
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            requestBuffers.put(clientChannel, new StringBuilder());
            System.out.println("[Accept] New connection from: " + clientChannel.getRemoteAddress());
        }
    }
    
    private void handleRead(SelectionKey key) throws IOException {
        var channel = (SocketChannel) key.channel();
        var buffer = ByteBuffer.allocate(4096);
        
        int bytesRead = channel.read(buffer);
        
        if (bytesRead == -1) {
            // Connection closed
            var connectionId = connectionIds.remove(channel);
            if (connectionId != null) {
                connectionManager.removeConnection(connectionId);
            }
            requestBuffers.remove(channel);
            channel.close();
            key.cancel();
            return;
        }
        
        buffer.flip();
        
        // Check if this is a WebSocket connection or initial HTTP handshake
        var connectionId = connectionIds.get(channel);
        
        if (connectionId == null) {
            // Still doing HTTP handshake
            var requestBuffer = requestBuffers.get(channel);
            var data = StandardCharsets.UTF_8.decode(buffer).toString();
            requestBuffer.append(data);
            
            // Check if we have complete HTTP request
            if (requestBuffer.toString().contains("\r\n\r\n")) {
                try {
                    connectionId = webSocketHandler.handleHandshake(channel, requestBuffer.toString());
                    connectionIds.put(channel, connectionId);
                    requestBuffers.remove(channel);
                } catch (Exception e) {
                    System.err.println("[Handshake] Error: " + e.getMessage());
                    channel.close();
                    key.cancel();
                }
            }
        } else {
            // Handle WebSocket frame
            webSocketHandler.handleFrame(connectionId, channel, buffer);
        }
    }
    
    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("⚠️  SIGTERM received. Initiating graceful shutdown...");
            System.out.println("=".repeat(60));
            
            isShuttingDown.set(true);
            registrationService.markDraining();
            
            // Stop accepting new connections
            try {
                serverChannel.close();
                System.out.println("[Shutdown] Stopped accepting new connections");
            } catch (IOException e) {
                System.err.println("[Shutdown] Error closing server channel: " + e.getMessage());
            }
            
            // Wait for connections to drain naturally
            int secondsRemaining = 30;
            System.out.println("[Shutdown] Draining connections (max 30s)...");
            
            while (connectionManager.getActiveConnectionCount() > 0 && secondsRemaining > 0) {
                System.out.println("[Shutdown] " + connectionManager.getActiveConnectionCount() + 
                                 " connections remaining... (" + secondsRemaining + "s)");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                secondsRemaining--;
            }
            
            // Force close any remaining connections
            if (connectionManager.getActiveConnectionCount() > 0) {
                System.out.println("[Shutdown] Force closing " + 
                                 connectionManager.getActiveConnectionCount() + " remaining connections");
                connectionManager.closeAllConnections();
            }
            
            // Deregister from service registry
            registrationService.deregister();
            
            System.out.println("=".repeat(60));
            System.out.println("✓ Graceful shutdown complete");
            System.out.println("=".repeat(60));
        }));
    }
    
    public static void main(String[] args) {
        try {
            var nodeId = System.getenv().getOrDefault("NODE_ID", "gateway-local");
            var redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
            var redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
            var websocketPort = Integer.parseInt(System.getenv().getOrDefault("WEBSOCKET_PORT", "8080"));
            var healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", "8081"));
            
            var gateway = new GatewayNode(nodeId, redisHost, redisPort, websocketPort, healthPort);
            gateway.start();
            
        } catch (Exception e) {
            System.err.println("Fatal error starting gateway: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
