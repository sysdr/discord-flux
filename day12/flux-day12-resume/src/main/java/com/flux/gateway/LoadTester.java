package com.flux.gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTester {
    private static final String WS_URL = "ws://localhost:8080";
    private static final int CLIENT_COUNT = 100;
    private static final int DISCONNECT_COUNT = 50;
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting load test with " + CLIENT_COUNT + " clients...");
        
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        CountDownLatch connectLatch = new CountDownLatch(CLIENT_COUNT);
        AtomicInteger connectedCount = new AtomicInteger(0);
        ConcurrentHashMap<Integer, TestClient> clients = new ConcurrentHashMap<>();
        
        // Phase 1: Connect all clients
        System.out.println("\nPhase 1: Connecting " + CLIENT_COUNT + " clients...");
        for (int i = 0; i < CLIENT_COUNT; i++) {
            final int clientId = i;
            Thread.ofVirtual().start(() -> {
                try {
                    TestClient client = new TestClient(httpClient, clientId);
                    client.connect();
                    clients.put(clientId, client);
                    connectedCount.incrementAndGet();
                    connectLatch.countDown();
                } catch (Exception e) {
                    System.err.println("Client " + clientId + " failed to connect: " + e.getMessage());
                    connectLatch.countDown();
                }
            });
        }
        
        connectLatch.await(30, TimeUnit.SECONDS);
        System.out.println("Connected: " + connectedCount.get() + "/" + CLIENT_COUNT);
        
        Thread.sleep(2000);
        
        // Phase 2: Disconnect half the clients
        System.out.println("\nPhase 2: Disconnecting " + DISCONNECT_COUNT + " clients...");
        for (int i = 0; i < DISCONNECT_COUNT; i++) {
            TestClient client = clients.get(i);
            if (client != null) {
                client.disconnect();
            }
        }
        
        Thread.sleep(3000);
        
        // Phase 3: Resume disconnected clients
        System.out.println("\nPhase 3: Resuming " + DISCONNECT_COUNT + " clients...");
        CountDownLatch resumeLatch = new CountDownLatch(DISCONNECT_COUNT);
        AtomicInteger resumedCount = new AtomicInteger(0);
        
        for (int i = 0; i < DISCONNECT_COUNT; i++) {
            final int clientId = i;
            Thread.ofVirtual().start(() -> {
                try {
                    TestClient client = clients.get(clientId);
                    if (client != null && client.resume()) {
                        resumedCount.incrementAndGet();
                    }
                    resumeLatch.countDown();
                } catch (Exception e) {
                    System.err.println("Client " + clientId + " failed to resume: " + e.getMessage());
                    resumeLatch.countDown();
                }
            });
        }
        
        resumeLatch.await(30, TimeUnit.SECONDS);
        System.out.println("Resumed: " + resumedCount.get() + "/" + DISCONNECT_COUNT);
        
        Thread.sleep(2000);
        
        System.out.println("\nLoad test complete!");
        System.out.println("Check the dashboard at http://localhost:8081/dashboard");
    }
    
    static class TestClient {
        private final HttpClient httpClient;
        private final int clientId;
        private WebSocket webSocket;
        private String sessionId;
        private long lastSeq = 0;
        
        TestClient(HttpClient httpClient, int clientId) {
            this.httpClient = httpClient;
            this.clientId = clientId;
        }
        
        void connect() throws Exception {
            CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URL), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        String msg = data.toString();
                        if (msg.contains("\"op\":10")) { // HELLO
                            int start = msg.indexOf("\"session_id\":\"") + 14;
                            int end = msg.indexOf("\"", start);
                            if (start > 13 && end > start) {
                                sessionId = msg.substring(start, end);
                                // Send IDENTIFY
                                String identify = "{\"op\":2,\"d\":{\"session_id\":\"" + sessionId + "\"}}";
                                webSocket.sendText(identify, true);
                            }
                        } else if (msg.contains("\"op\":0")) { // DISPATCH
                            // Extract sequence number
                            int seqStart = msg.indexOf("\"s\":");
                            if (seqStart > 0) {
                                int seqEnd = msg.indexOf(",", seqStart);
                                if (seqEnd < 0) seqEnd = msg.indexOf("}", seqStart);
                                if (seqEnd > seqStart) {
                                    try {
                                        String seqStr = msg.substring(seqStart + 4, seqEnd).trim();
                                        lastSeq = Long.parseLong(seqStr);
                                    } catch (NumberFormatException e) {
                                        // Ignore
                                    }
                                }
                            }
                        }
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }
                });
            
            webSocket = wsFuture.get(10, TimeUnit.SECONDS);
        }
        
        void disconnect() {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test disconnect");
            }
        }
        
        boolean resume() throws Exception {
            if (sessionId == null) return false;
            
            CompletableFuture<Boolean> resumeFuture = new CompletableFuture<>();
            
            webSocket = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URL), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        String msg = data.toString();
                        if (msg.contains("\"op\":10")) { // HELLO
                            // Send proper RESUME with last sequence
                            String resume = "{\"op\":6,\"d\":{\"session_id\":\"" + 
                                            sessionId + "\",\"seq\":" + lastSeq + "}}";
                            webSocket.sendText(resume, true);
                        } else if (msg.contains("\"op\":7")) { // RESUMED
                            resumeFuture.complete(true);
                        } else if (msg.contains("\"op\":9")) { // INVALID_SESSION
                            resumeFuture.complete(false);
                        }
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }
                }).get(10, TimeUnit.SECONDS);
            
            return resumeFuture.get(10, TimeUnit.SECONDS);
        }
    }
}
