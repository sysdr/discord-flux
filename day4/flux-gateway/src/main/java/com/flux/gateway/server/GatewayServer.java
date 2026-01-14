package com.flux.gateway.server;

import com.flux.gateway.handler.PacketHandler;
import com.flux.gateway.protocol.*;
import com.flux.gateway.util.Metrics;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Core Gateway server using NIO Selector + Virtual Threads.
 * Accepts connections, reads opcodes, dispatches to handlers.
 */
public class GatewayServer implements AutoCloseable {
    
    private final int port;
    private final Selector selector;
    private final ServerSocketChannel serverSocket;
    private final Map<SocketChannel, ConnectionState> connections;
    private final ExecutorService handlerExecutor;
    private final PacketHandler packetHandler;
    private final Metrics metrics;
    private volatile boolean running;
    
    public GatewayServer(int port) throws IOException {
        this.port = port;
        this.selector = Selector.open();
        this.serverSocket = ServerSocketChannel.open();
        this.connections = new ConcurrentHashMap<>();
        this.handlerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.packetHandler = new PacketHandler();
        this.metrics = Metrics.getInstance();
        
        serverSocket.bind(new InetSocketAddress(port));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("✅ Gateway listening on port " + port);
    }
    
    public void start() {
        running = true;
        
        // Start heartbeat checker thread
        Thread.ofVirtual().start(this::heartbeatChecker);
        
        // Main selector loop
        while (running) {
            try {
                selector.select(1000); // 1s timeout
                
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    
                    if (!key.isValid()) continue;
                    
                    if (key.isAcceptable()) {
                        acceptConnection();
                    } else if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        handlerExecutor.submit(() -> readPacket(channel));
                    }
                }
            } catch (IOException e) {
                System.err.println("Selector error: " + e.getMessage());
            }
        }
    }
    
    private void acceptConnection() {
        try {
            SocketChannel client = serverSocket.accept();
            if (client == null) return;
            
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
            
            ConnectionState state = new ConnectionState(client);
            connections.put(client, state);
            metrics.incrementConnections();
            
            System.out.println("📥 New connection: " + client.getRemoteAddress());
            
            // Send Hello packet
            Hello hello = new Hello(30000); // 30s heartbeat interval
            writePacket(client, hello);
            
        } catch (IOException e) {
            System.err.println("Accept error: " + e.getMessage());
        }
    }
    
    private void readPacket(SocketChannel channel) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        
        try {
            int bytesRead = channel.read(buffer);
            
            if (bytesRead == -1) {
                closeConnection(channel);
                return;
            }
            
            if (bytesRead == 0) return;
            
            buffer.flip();
            
            // Decode packet (zero-copy)
            GatewayPacket packet = GatewayPacket.decode(buffer);
            
            // Track opcode metrics
            metrics.recordOpcode(packet.opcode());
            
            // Handle packet
            ConnectionState state = connections.get(channel);
            if (state != null) {
                handlePacket(channel, state, packet);
            }
            
        } catch (IOException e) {
            closeConnection(channel);
        } catch (ProtocolException e) {
            System.err.println("Protocol error: " + e.getMessage());
            closeConnection(channel);
        }
    }
    
    private void handlePacket(SocketChannel channel, ConnectionState state, GatewayPacket packet) {
        switch (packet) {
            case Heartbeat hb -> {
                state.updateHeartbeat();
                HeartbeatAck ack = new HeartbeatAck();
                writePacket(channel, ack);
            }
            case Identify id -> {
                // Simple token validation (production would check database)
                if (id.token().startsWith("flux_")) {
                    state.transitionTo(ConnectionState.State.UNAUTHENTICATED, 
                                     ConnectionState.State.IDENTIFIED);
                    System.out.println("✅ Client identified: " + id.token());
                } else {
                    System.out.println("❌ Invalid token: " + id.token());
                    closeConnection(channel);
                }
            }
            case InvalidPacket inv -> {
                System.err.println("Invalid packet: " + inv.reason());
                closeConnection(channel);
            }
            default -> {
                System.out.println("Received packet: " + packet.getClass().getSimpleName());
            }
        }
    }
    
    private void writePacket(SocketChannel channel, GatewayPacket packet) {
        try {
            ByteBuffer buffer = packet.encode();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        } catch (IOException e) {
            System.err.println("Write error: " + e.getMessage());
            closeConnection(channel);
        }
    }
    
    private void closeConnection(SocketChannel channel) {
        try {
            connections.remove(channel);
            metrics.decrementConnections();
            channel.close();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    private void heartbeatChecker() {
        while (running) {
            try {
                Thread.sleep(5000); // Check every 5s
                
                connections.values().forEach(state -> {
                    if (state.isTimedOut(45000)) { // 45s timeout
                        System.out.println("⏰ Connection timeout, closing...");
                        closeConnection(state.getChannel());
                    }
                });
                
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    public Map<SocketChannel, ConnectionState> getConnections() {
        return connections;
    }
    
    public Metrics getMetrics() {
        return metrics;
    }
    
    @Override
    public void close() {
        running = false;
        handlerExecutor.close();
        
        connections.keySet().forEach(this::closeConnection);
        
        try {
            selector.close();
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("Shutdown error: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        try (GatewayServer server = new GatewayServer(9000)) {
            server.start();
        } catch (IOException e) {
            System.err.println("Server failed: " + e.getMessage());
        }
    }
}
