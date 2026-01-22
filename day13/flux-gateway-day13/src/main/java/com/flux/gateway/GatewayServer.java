package com.flux.gateway;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GatewayServer {
    private final int port;
    private final ReplayBufferManager bufferManager;
    private final ConcurrentHashMap<String, WebSocketSession> sessions;
    private final ScheduledExecutorService messagePublisher;
    private final AtomicInteger connectionCounter = new AtomicInteger(0);
    private volatile boolean running = true;
    
    public GatewayServer(int port) {
        this.port = port;
        this.bufferManager = new ReplayBufferManager(256, TimeUnit.MINUTES.toMillis(5));
        this.sessions = new ConcurrentHashMap<>();
        this.messagePublisher = Executors.newSingleThreadScheduledExecutor();
    }
    
    public void start() throws IOException {
        DashboardServer dashboard = new DashboardServer(8080, this);
        Thread dashboardThread = Thread.ofVirtual().start(dashboard::start);
        
        System.out.println("[INFO] Starting WebSocket Gateway on port " + port);
        System.out.println("[INFO] Dashboard available at http://localhost:8080/dashboard");
        System.out.println("[INFO] Replay Buffer initialized (capacity: 256 per user)");
        
        // Start message publisher (simulates server-side events)
        messagePublisher.scheduleAtFixedRate(this::publishMessages, 1, 1, TimeUnit.SECONDS);
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (running) {
                Socket client = serverSocket.accept();
                Thread.ofVirtual().start(() -> handleClient(client));
            }
        }
    }
    
    private void handleClient(Socket client) {
        String sessionId = "session-" + connectionCounter.incrementAndGet();
        
        try {
            WebSocketSession session = new WebSocketSession(client, sessionId);
            
            if (!session.performHandshake()) {
                client.close();
                return;
            }
            
            sessions.put(sessionId, session);
            System.out.println("[CONNECT] " + sessionId + " connected");
            
            // Send welcome message
            String welcome = "WELCOME|" + sessionId;
            bufferManager.bufferMessage(sessionId, welcome.getBytes(StandardCharsets.UTF_8));
            session.sendMessage(welcome.getBytes(StandardCharsets.UTF_8));
            
            // Keep connection alive
            byte[] buffer = new byte[4096];
            while (session.isConnected()) {
                int read = client.getInputStream().read(buffer);
                if (read <= 0) break;
            }
            
        } catch (IOException e) {
            // Connection closed
        } finally {
            sessions.remove(sessionId);
            System.out.println("[DISCONNECT] " + sessionId + " disconnected");
        }
    }
    
    private void publishMessages() {
        sessions.values().forEach(session -> {
            try {
                long timestamp = System.currentTimeMillis();
                String message = "MSG|" + timestamp;
                byte[] payload = message.getBytes(StandardCharsets.UTF_8);
                
                long sequence = bufferManager.bufferMessage(session.getSessionId(), payload);
                session.setLastSequence(sequence);
                session.sendMessage(payload);
            } catch (IOException e) {
                // Client disconnected
            }
        });
    }
    
    public int getActiveConnections() {
        return sessions.size();
    }
    
    public ReplayBufferManager getBufferManager() {
        return bufferManager;
    }
    
    public void shutdown() {
        running = false;
        messagePublisher.shutdown();
        bufferManager.shutdown();
        sessions.values().forEach(WebSocketSession::disconnect);
    }
    
    public static void main(String[] args) throws IOException {
        new GatewayServer(9001).start();
    }
}
