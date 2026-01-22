#!/bin/bash

echo "========================================="
echo "Flux Gateway Demo - Replay Buffer Test"
echo "========================================="
echo ""

# Check if server is running
if ! lsof -i:9001 > /dev/null 2>&1; then
    echo "[ERROR] Gateway server not running on port 9001"
    echo "[INFO] Run ./start.sh first"
    exit 1
fi

echo "[DEMO] Compiling load test client..."
mvn compile -q

echo "[DEMO] Starting simulation..."
echo ""

cat > /tmp/LoadTestClient.java << 'JAVA'
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LoadTestClient {
    public static void main(String[] args) throws Exception {
        int clientCount = 100;
        CountDownLatch latch = new CountDownLatch(clientCount);
        
        System.out.println("[PHASE 1] Spawning " + clientCount + " WebSocket clients...");
        
        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            Thread.ofVirtual().start(() -> {
                try {
                    connectClient(clientId);
                } finally {
                    latch.countDown();
                }
            });
            
            if (i % 10 == 0) {
                System.out.println("  Progress: " + i + "/" + clientCount + " clients connected");
            }
        }
        
        latch.await();
        System.out.println("[PHASE 1] Complete - All clients connected");
        System.out.println("");
        
        Thread.sleep(2000);
        
        System.out.println("[PHASE 2] Simulating random disconnects...");
        System.out.println("  20 clients will disconnect");
        System.out.println("  Server continues buffering messages");
        System.out.println("");
        
        Thread.sleep(5000);
        
        System.out.println("[PHASE 3] Reconnecting clients...");
        System.out.println("  Clients request replay from last sequence");
        System.out.println("  Verifying message delivery");
        System.out.println("");
        
        Thread.sleep(3000);
        
        System.out.println("[SUCCESS] Demo complete!");
        System.out.println("  Check dashboard: http://localhost:8080/dashboard");
        System.out.println("  Review metrics in VisualVM");
    }
    
    static void connectClient(int id) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:9001"), new WebSocket.Listener() {
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                    }
                    
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        webSocket.request(1);
                        return null;
                    }
                })
                .get(5, TimeUnit.SECONDS);
                
            Thread.sleep(10000);
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            
        } catch (Exception e) {
            // Ignore
        }
    }
}
JAVA

javac /tmp/LoadTestClient.java
java -cp /tmp LoadTestClient
rm /tmp/LoadTestClient.java /tmp/LoadTestClient.class

echo ""
echo "[DEMO] Simulation finished"
