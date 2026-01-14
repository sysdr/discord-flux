package com.flux.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public final class GatewayServer {
    private final int port;
    private final ConnectionRegistry registry;
    private final HeartbeatManager heartbeatManager;
    private final Metrics metrics;
    private final AtomicInteger nextConnectionId = new AtomicInteger(0);
    private volatile boolean running = false;
    
    public GatewayServer(int port) {
        this.port = port;
        this.registry = new ConnectionRegistry();
        this.metrics = new Metrics();
        this.heartbeatManager = new HeartbeatManager(registry, this, metrics);
    }
    
    public void start() throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        
        Selector selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        running = true;
        heartbeatManager.start();
        
        System.out.println("🚀 Gateway server started on port " + port);
        System.out.println("📊 Dashboard: http://localhost:8081/dashboard");
        
        while (running) {
            selector.select(1000);
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                
                if (!key.isValid()) continue;
                
                if (key.isAcceptable()) {
                    acceptConnection(serverChannel, selector);
                } else if (key.isReadable()) {
                    readFromConnection(key);
                }
            }
        }
    }
    
    private void acceptConnection(ServerSocketChannel serverChannel, Selector selector) {
        try {
            SocketChannel clientChannel = serverChannel.accept();
            clientChannel.configureBlocking(false);
            
            int id = nextConnectionId.getAndIncrement();
            Connection conn = new Connection(id, clientChannel);
            
            // Read HTTP handshake request first
            ByteBuffer handshakeBuffer = ByteBuffer.allocate(1024);
            int bytesRead = clientChannel.read(handshakeBuffer);
            if (bytesRead > 0) {
                handshakeBuffer.flip();
                String request = new String(handshakeBuffer.array(), 0, bytesRead);
                if (request.contains("Upgrade: websocket")) {
                    // Send WebSocket handshake response
                    sendHandshake(clientChannel);
                    
                    // Register connection after handshake
                    registry.register(conn);
                    clientChannel.register(selector, SelectionKey.OP_READ, conn);
                    
                    System.out.println("✅ Connection accepted: " + id);
                } else {
                    clientChannel.close();
                }
            } else {
                clientChannel.close();
            }
        } catch (IOException e) {
            System.err.println("Failed to accept connection: " + e.getMessage());
        }
    }
    
    private void sendHandshake(SocketChannel channel) throws IOException {
        String response = """
            HTTP/1.1 101 Switching Protocols\r
            Upgrade: websocket\r
            Connection: Upgrade\r
            Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r
            \r
            """;
        channel.write(ByteBuffer.wrap(response.getBytes()));
    }
    
    private void readFromConnection(SelectionKey key) {
        Connection conn = (Connection) key.attachment();
        try {
            conn.readBuffer().clear();
            int bytesRead = conn.channel().read(conn.readBuffer());
            
            if (bytesRead == -1) {
                closeConnection(conn, "client_disconnect");
                key.cancel();
                return;
            }
            
            if (bytesRead > 0) {
                conn.readBuffer().flip();
                processFrame(conn);
            }
        } catch (IOException e) {
            closeConnection(conn, "read_error");
            key.cancel();
        }
    }
    
    private void processFrame(Connection conn) {
        ByteBuffer buffer = conn.readBuffer();
        
        // Skip WebSocket frame header (simplified parsing)
        if (buffer.remaining() < 2) return;
        
        buffer.get(); // FIN + opcode
        byte maskAndLength = buffer.get();
        boolean masked = (maskAndLength & 0x80) != 0;
        int payloadLength = maskAndLength & 0x7F;
        
        if (buffer.remaining() < (masked ? 4 : 0) + payloadLength) return;
        
        // Read mask key if masked
        byte[] maskKey = new byte[4];
        if (masked) {
            buffer.get(maskKey);
        }
        
        // Read payload
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        
        // Unmask payload if masked
        if (masked) {
            for (int i = 0; i < payloadLength; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }
        
        String message = new String(payload);
        
        // Parse opcode from JSON
        if (message.contains("\"op\":11")) {
            heartbeatManager.handleAck(conn.id());
        }
    }
    
    public void closeConnection(Connection conn, String reason) {
        try {
            conn.channel().close();
            registry.unregister(conn.id());
            System.out.println("❌ Connection closed: " + conn.id() + " (" + reason + ")");
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
    
    public void stop() {
        running = false;
        heartbeatManager.stop();
    }
    
    public ConnectionRegistry getRegistry() {
        return registry;
    }
    
    public Metrics getMetrics() {
        return metrics;
    }
}
