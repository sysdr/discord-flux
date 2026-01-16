package com.flux.gateway.concurrency;

import com.flux.gateway.concurrency.common.ServerInterface;
import com.flux.gateway.concurrency.nioreactor.NioReactorServer;
import com.flux.gateway.concurrency.threadper.ThreadPerConnectionServer;
import com.flux.gateway.concurrency.virtual.VirtualThreadServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class ServerTest {
    
    @Test
    void testThreadPerConnectionServer() throws Exception {
        testServer(new ThreadPerConnectionServer(0)); // Port 0 = random free port
    }
    
    @Test
    void testNioReactorServer() throws Exception {
        testServer(new NioReactorServer(0));
    }
    
    @Test
    void testVirtualThreadServer() throws Exception {
        testServer(new VirtualThreadServer(0));
    }
    
    private void testServer(ServerInterface server) throws Exception {
        server.start();
        
        // Wait for server to start and be ready (with retry logic)
        int port = server.getPort();
        int retries = 10;
        boolean connected = false;
        
        while (retries > 0 && !connected) {
            Thread.sleep(200); // Wait a bit longer
            try (Socket testSocket = new Socket("localhost", port)) {
                connected = true;
                testSocket.close();
            } catch (IOException e) {
                retries--;
                if (retries == 0) {
                    throw new AssertionError("Server failed to start on port " + port + " after 2 seconds", e);
                }
            }
        }
        
        try {
            // Test single connection echo
            try (Socket socket = new Socket("localhost", port)) {
                var out = socket.getOutputStream();
                var in = socket.getInputStream();
                
                String message = "Hello World\n";
                out.write(message.getBytes());
                out.flush();
                
                byte[] buffer = new byte[1024];
                int bytesRead = in.read(buffer);
                assertTrue(bytesRead > 0, "Should have received response");
                
                String response = new String(buffer, 0, bytesRead);
                assertEquals(message, response, "Server should echo message");
            }
            
            // Verify metrics
            var metrics = server.getMetrics().snapshot();
            assertTrue(metrics.totalConnections() >= 1, "Should have at least 1 connection");
            assertTrue(metrics.messagesReceived() >= 1, "Should have received messages");
            
        } finally {
            server.stop();
        }
    }
}
