package com.flux.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class DashboardServer {
    private final int port;
    private final LocalConnectionRegistry registry;
    private final BroadcastEngine broadcastEngine;
    private HttpServer server;
    
    public DashboardServer(
        int port, 
        LocalConnectionRegistry registry, 
        BroadcastEngine broadcastEngine
    ) {
        this.port = port;
        this.registry = registry;
        this.broadcastEngine = broadcastEngine;
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/dashboard.html", exchange -> {
            String html = generateDashboard();
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.createContext("/api/stats", exchange -> {
            String json = generateStatsJson();
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.setExecutor(null); // Use default executor
        server.start();
        
        System.out.println("[DASHBOARD] Started at http://localhost:" + port + "/dashboard.html");
    }
    
    private String generateStatsJson() {
        return String.format("""
            {
              "activeConnections": %d,
              "totalBroadcasts": %d,
              "totalRecipients": %d,
              "totalBytesSerialized": %d,
              "connections": [%s]
            }
            """,
            registry.size(),
            broadcastEngine.getTotalBroadcasts(),
            broadcastEngine.getTotalRecipients(),
            broadcastEngine.getTotalBytesSerialized(),
            generateConnectionsJson()
        );
    }
    
    private String generateConnectionsJson() {
        return registry.getAllConnections().stream()
            .limit(100) // Limit to first 100 for performance
            .map(conn -> String.format("""
                {"sessionId":"%s","writeQueueDepth":%d,"messagesSent":%d}
                """.trim(),
                conn.sessionId(),
                conn.writeQueueDepth(),
                conn.messagesSent()
            ))
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    }
    
    private String generateDashboard() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Flux Gateway - Local Fan-Out Dashboard</title>
                <style>
                    body {
                        font-family: 'Courier New', monospace;
                        background: #1a1a1a;
                        color: #00ff00;
                        padding: 20px;
                    }
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                    }
                    h1 {
                        color: #00ff00;
                        border-bottom: 2px solid #00ff00;
                        padding-bottom: 10px;
                    }
                    .stats-grid {
                        display: grid;
                        grid-template-columns: repeat(4, 1fr);
                        gap: 20px;
                        margin: 20px 0;
                    }
                    .stat-card {
                        background: #2a2a2a;
                        border: 1px solid #00ff00;
                        padding: 15px;
                        border-radius: 5px;
                    }
                    .stat-value {
                        font-size: 32px;
                        font-weight: bold;
                        color: #00ff00;
                    }
                    .stat-label {
                        font-size: 12px;
                        color: #888;
                        margin-top: 5px;
                    }
                    .connection-grid {
                        display: grid;
                        grid-template-columns: repeat(10, 1fr);
                        gap: 5px;
                        margin: 20px 0;
                    }
                    .connection-cell {
                        width: 100%;
                        aspect-ratio: 1;
                        border: 1px solid #444;
                        border-radius: 3px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 10px;
                    }
                    .connection-active {
                        background: #00ff00;
                        color: #000;
                    }
                    .connection-slow {
                        background: #ff3300;
                        color: #fff;
                    }
                    .connection-empty {
                        background: #2a2a2a;
                    }
                    button {
                        background: #00ff00;
                        color: #000;
                        border: none;
                        padding: 10px 20px;
                        margin: 5px;
                        cursor: pointer;
                        font-family: 'Courier New', monospace;
                        font-weight: bold;
                    }
                    button:hover {
                        background: #00cc00;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>FLUX GATEWAY - LOCAL FAN-OUT MONITOR</h1>
                    
                    <div class="stats-grid">
                        <div class="stat-card">
                            <div class="stat-value" id="activeConnections">0</div>
                            <div class="stat-label">ACTIVE CONNECTIONS</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value" id="totalBroadcasts">0</div>
                            <div class="stat-label">TOTAL BROADCASTS</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value" id="totalRecipients">0</div>
                            <div class="stat-label">TOTAL RECIPIENTS</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value" id="totalBytes">0</div>
                            <div class="stat-label">BYTES SERIALIZED</div>
                        </div>
                    </div>
                    
                    <h2>Connection Grid (First 100)</h2>
                    <div class="connection-grid" id="connectionGrid"></div>
                    
                    <h2>Actions</h2>
                    <div>
                        <button onclick="alert('Use demo.sh script to spawn connections')">
                            Spawn 100 Connections
                        </button>
                        <button onclick="alert('Publish to Redis using: redis-cli PUBLISH guild_events ...')">
                            Send Test Broadcast
                        </button>
                    </div>
                </div>
                
                <script>
                    async function updateStats() {
                        try {
                            const response = await fetch('/api/stats');
                            const data = await response.json();
                            
                            document.getElementById('activeConnections').textContent = data.activeConnections;
                            document.getElementById('totalBroadcasts').textContent = data.totalBroadcasts;
                            document.getElementById('totalRecipients').textContent = data.totalRecipients;
                            document.getElementById('totalBytes').textContent = 
                                (data.totalBytesSerialized / 1024).toFixed(2) + ' KB';
                            
                            updateConnectionGrid(data.connections);
                        } catch (e) {
                            console.error('Failed to fetch stats:', e);
                        }
                    }
                    
                    function updateConnectionGrid(connections) {
                        const grid = document.getElementById('connectionGrid');
                        grid.innerHTML = '';
                        
                        for (let i = 0; i < 100; i++) {
                            const cell = document.createElement('div');
                            cell.className = 'connection-cell';
                            
                            if (i < connections.length) {
                                const conn = connections[i];
                                const queueDepth = conn.writeQueueDepth;
                                
                                if (queueDepth > 500) {
                                    cell.className += ' connection-slow';
                                    cell.textContent = queueDepth;
                                } else {
                                    cell.className += ' connection-active';
                                    cell.textContent = '✓';
                                }
                            } else {
                                cell.className += ' connection-empty';
                            }
                            
                            grid.appendChild(cell);
                        }
                    }
                    
                    // Update every 1 second
                    setInterval(updateStats, 1000);
                    updateStats();
                </script>
            </body>
            </html>
            """;
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
