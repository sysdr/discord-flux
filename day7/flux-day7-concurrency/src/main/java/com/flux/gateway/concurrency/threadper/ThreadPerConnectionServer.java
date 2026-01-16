package com.flux.gateway.concurrency.threadper;

import com.flux.gateway.concurrency.common.ServerInterface;
import com.flux.gateway.concurrency.common.ServerMetrics;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadPerConnectionServer implements ServerInterface {
    private final int port;
    private final ServerMetrics metrics = new ServerMetrics();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread acceptThread;
    
    public ThreadPerConnectionServer(int port) {
        this.port = port;
    }
    
    @Override
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running.set(true);
        
        acceptThread = new Thread(() -> {
            System.out.println("[ThreadPerConnection] Server started on port " + port);
            while (running.get()) {
                try {
                    Socket client = serverSocket.accept();
                    metrics.connectionAccepted();
                    
                    // Spawn new OS thread per connection
                    new Thread(() -> handleClient(client)).start();
                    
                } catch (SocketException e) {
                    if (!running.get()) break;
                } catch (IOException e) {
                    System.err.println("Accept error: " + e.getMessage());
                }
            }
        }, "ThreadPerConnection-Acceptor");
        
        acceptThread.start();
    }
    
    private void handleClient(Socket socket) {
        try (socket) {
            var in = socket.getInputStream();
            var out = socket.getOutputStream();
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                metrics.messageReceived(bytesRead);
                out.write(buffer, 0, bytesRead); // Echo back
                metrics.messageSent(bytesRead);
            }
            
        } catch (IOException e) {
            // Connection closed
        } finally {
            metrics.connectionClosed();
        }
    }
    
    @Override
    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null) serverSocket.close();
            if (acceptThread != null) acceptThread.join(5000);
        } catch (Exception e) {
            System.err.println("Shutdown error: " + e.getMessage());
        }
    }
    
    @Override
    public void close() {
        stop();
    }
    
    @Override
    public ServerMetrics getMetrics() {
        return metrics;
    }
    
    @Override
    public int getPort() {
        // If port was 0 (random), return the actual bound port
        if (port == 0 && serverSocket != null) {
            return serverSocket.getLocalPort();
        }
        return port;
    }
    
    @Override
    public String getType() {
        return "ThreadPerConnection";
    }
}
