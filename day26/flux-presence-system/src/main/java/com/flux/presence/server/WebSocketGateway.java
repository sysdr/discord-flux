package com.flux.presence.server;

import com.flux.presence.core.PresenceService;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Simulated WebSocket Gateway that accepts connections and manages presence.
 */
public class WebSocketGateway implements AutoCloseable {
    
    private static final Logger logger = Logger.getLogger(WebSocketGateway.class.getName());
    private static final int GATEWAY_PORT = 9090;
    
    private final PresenceService presenceService;
    private final ServerSocket serverSocket;
    private final ExecutorService executor;
    private volatile boolean running = true;
    
    public WebSocketGateway(PresenceService presenceService) throws IOException {
        this.presenceService = presenceService;
        this.serverSocket = new ServerSocket(GATEWAY_PORT);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        logger.info("WebSocket Gateway started on port " + GATEWAY_PORT);
    }
    
    public void start() {
        executor.submit(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    executor.submit(() -> handleClient(client));
                } catch (IOException e) {
                    if (running) {
                        logger.warning("Accept failed: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    private void handleClient(Socket client) {
        try (client;
             var in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             var out = new PrintWriter(client.getOutputStream(), true)) {
            
            // Simulate user ID assignment
            long userId = (long)(Math.random() * 1_000_000);
            logger.info("Client connected: user " + userId);
            
            // Mark user online
            presenceService.markOnline(userId);
            out.println("CONNECTED:user_id=" + userId);
            
            // Simulate heartbeat loop
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("PING")) {
                    presenceService.markOnline(userId); // Refresh presence
                    out.println("PONG");
                } else if (line.equals("DISCONNECT")) {
                    break;
                }
            }
            
            // Mark user offline
            presenceService.markOffline(userId).join();
            logger.info("Client disconnected: user " + userId);
            
        } catch (Exception e) {
            logger.warning("Client handler error: " + e.getMessage());
        }
    }
    
    @Override
    public void close() throws Exception {
        running = false;
        serverSocket.close();
        executor.shutdown();
        logger.info("WebSocket Gateway stopped");
    }
}
