package com.flux.loadbalancer;

import com.flux.loadbalancer.dashboard.DashboardServer;
import com.flux.loadbalancer.models.GatewayNode;
import com.google.gson.Gson;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoadBalancer {
    private static final String REGISTRY_KEY = "gateway:nodes";
    private final JedisPool jedisPool;
    private final ConsistentHash consistentHash;
    private final int proxyPort;
    private final Selector selector;
    private final Map<SocketChannel, SocketChannel> clientToGateway = new ConcurrentHashMap<>();
    private final Map<SocketChannel, ByteBuffer> pendingClientData = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    
    public LoadBalancer(String redisHost, int redisPort, int proxyPort, int dashboardPort) throws IOException {
        var config = new JedisPoolConfig();
        config.setMaxTotal(16);
        this.jedisPool = new JedisPool(config, redisHost, redisPort);
        this.consistentHash = new ConsistentHash();
        this.proxyPort = proxyPort;
        this.selector = Selector.open();
        
        // Start dashboard server
        var dashboard = new DashboardServer(jedisPool, dashboardPort);
        dashboard.start();
        
        // Start membership updater
        startMembershipUpdater();
    }
    
    public void start() throws IOException {
        System.out.println("=".repeat(60));
        System.out.println("🔀 Starting Flux Load Balancer");
        System.out.println("=".repeat(60));
        
        var serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(proxyPort));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("✓ Load Balancer started");
        System.out.println("  - Proxy Port: " + proxyPort);
        System.out.println("=".repeat(60));
        
        // Main event loop
        while (true) {
            selector.select(1000);
            
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
                    } else if (key.isConnectable()) {
                        handleConnect(key);
                    }
                } catch (Exception e) {
                    System.err.println("[LoadBalancer] Error: " + e.getMessage());
                    cleanupConnection(key);
                }
            }
        }
    }
    
    private void handleAccept(SelectionKey key) throws IOException {
        var serverChannel = (ServerSocketChannel) key.channel();
        var clientChannel = serverChannel.accept();
        
        if (clientChannel == null) return;
        
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        
        System.out.println("[Accept] New client connection from: " + clientChannel.getRemoteAddress());
    }
    
    private void handleRead(SelectionKey key) throws IOException {
        var sourceChannel = (SocketChannel) key.channel();
        var buffer = ByteBuffer.allocate(8192);
        
        int bytesRead = sourceChannel.read(buffer);
        
        if (bytesRead == -1) {
            cleanupConnection(key);
            return;
        }
        
        buffer.flip();
        
        // Check if this is a client or gateway channel
        var peerChannel = clientToGateway.get(sourceChannel);
        
        if (peerChannel == null) {
            // This is a new client connection - establish gateway connection
            peerChannel = connectToGateway(sourceChannel);
            if (peerChannel != null) {
                clientToGateway.put(sourceChannel, peerChannel);
                clientToGateway.put(peerChannel, sourceChannel);
                // Buffer initial client data until gateway connection is ready (handleConnect)
                pendingClientData.put(peerChannel, buffer.duplicate());
            } else {
                System.err.println("[Read] No healthy gateways available");
                sourceChannel.close();
            }
        } else {
            // Forward data to peer
            while (buffer.hasRemaining()) {
                peerChannel.write(buffer);
            }
        }
    }
    
    private void handleConnect(SelectionKey key) throws IOException {
        var channel = (SocketChannel) key.channel();
        
        if (channel.finishConnect()) {
            key.interestOps(SelectionKey.OP_READ);
            System.out.println("[Connect] Connected to gateway: " + channel.getRemoteAddress());
            // Forward buffered client data now that gateway is connected
            var pending = pendingClientData.remove(channel);
            if (pending != null) {
                while (pending.hasRemaining()) {
                    channel.write(pending);
                }
            }
        }
    }
    
    private SocketChannel connectToGateway(SocketChannel clientChannel) throws IOException {
        // Use consistent hashing to select gateway
        // For simplicity, use client's IP as the hash key
        var clientAddress = clientChannel.getRemoteAddress().toString();
        var gateway = consistentHash.getNode(clientAddress);
        
        if (gateway == null || !gateway.isHealthy()) {
            return null;
        }
        
        var gatewayChannel = SocketChannel.open();
        gatewayChannel.configureBlocking(false);
        gatewayChannel.connect(new InetSocketAddress(gateway.ipAddress(), gateway.port()));
        gatewayChannel.register(selector, SelectionKey.OP_CONNECT);
        
        System.out.println("[Route] Client " + clientAddress + " -> " + gateway.nodeId());
        
        return gatewayChannel;
    }
    
    private void cleanupConnection(SelectionKey key) {
        var channel = (SocketChannel) key.channel();
        pendingClientData.remove(channel);
        var peerChannel = clientToGateway.remove(channel);
        
        if (peerChannel != null) {
            clientToGateway.remove(peerChannel);
            pendingClientData.remove(peerChannel);
            try {
                peerChannel.close();
            } catch (IOException ignored) {}
        }
        
        try {
            channel.close();
        } catch (IOException ignored) {}
        
        key.cancel();
    }
    
    private void startMembershipUpdater() {
        var executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r);
            thread.setName("membership-updater");
            thread.setDaemon(true);
            return thread;
        });
        
        executor.scheduleAtFixedRate(() -> {
            try {
                updateClusterMembership();
            } catch (Exception e) {
                System.err.println("[Membership] Update failed: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    private void updateClusterMembership() {
        try (var jedis = jedisPool.getResource()) {
            var entries = jedis.hgetAll(REGISTRY_KEY);
            var nodes = new ArrayList<GatewayNode>();
            
            entries.forEach((nodeId, json) -> {
                try {
                    var data = gson.fromJson(json, Map.class);
                    var node = new GatewayNode(
                        (String) data.get("nodeId"),
                        (String) data.get("ipAddress"),
                        numInt(data.get("port")),
                        numInt(data.get("currentConnections")),
                        Instant.ofEpochSecond(numLong(data.get("lastHeartbeat"))),
                        (String) data.get("status")
                    );
                    
                    if (node.isHealthy()) {
                        nodes.add(node);
                    }
                } catch (Exception e) {
                    System.err.println("[Membership] Error parsing node: " + e.getMessage());
                }
            });
            
            consistentHash.rebuild(nodes);
            
            if (nodes.size() != consistentHash.getNodeCount()) {
                System.out.println("[Membership] Updated: " + nodes.size() + " healthy nodes");
            }
        }
    }
    
    private static int numInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
    
    private static long numLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
    
    public static void main(String[] args) {
        try {
            var redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
            var redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
            var proxyPort = Integer.parseInt(System.getenv().getOrDefault("PROXY_PORT", "8080"));
            var dashboardPort = Integer.parseInt(System.getenv().getOrDefault("DASHBOARD_PORT", "9090"));
            
            var loadBalancer = new LoadBalancer(redisHost, redisPort, proxyPort, dashboardPort);
            loadBalancer.start();
            
        } catch (Exception e) {
            System.err.println("Fatal error starting load balancer: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
