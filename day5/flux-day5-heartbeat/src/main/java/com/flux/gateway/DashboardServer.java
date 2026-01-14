package com.flux.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public final class DashboardServer {
    private final GatewayServer gateway;
    private HttpServer server;
    
    public DashboardServer(GatewayServer gateway) {
        this.gateway = gateway;
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(8081), 0);
        
        server.createContext("/dashboard", exchange -> {
            String html = generateDashboard();
            byte[] htmlBytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, htmlBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(htmlBytes);
            }
        });
        
        server.createContext("/api/metrics", exchange -> {
            String metricsJson = gateway.getMetrics().toJson();
            int activeConnections = gateway.getRegistry().getActiveCount();
            // Parse metrics JSON and add activeConnections
            String json = metricsJson.substring(0, metricsJson.length() - 2) + 
                ",\n                \"activeConnections\": " + activeConnections + "\n            }";
            byte[] jsonBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, jsonBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsonBytes);
            }
        });
        
        server.setExecutor(null);
        server.start();
        System.out.println("📊 Dashboard started on http://localhost:8081/dashboard");
    }
    
    private String generateDashboard() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Flux Day 5: Heartbeat Monitor</title>
                <style>
                    body { 
                        font-family: 'Courier New', monospace; 
                        background: #0a0e27; 
                        color: #00ff41; 
                        padding: 20px;
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 30px;
                        border-bottom: 2px solid #00ff41;
                        padding-bottom: 10px;
                    }
                    .metrics {
                        display: grid;
                        grid-template-columns: repeat(3, 1fr);
                        gap: 20px;
                        margin-bottom: 30px;
                    }
                    .metric-card {
                        background: #1a1f3a;
                        padding: 20px;
                        border-radius: 8px;
                        border: 1px solid #00ff41;
                    }
                    .metric-value {
                        font-size: 32px;
                        font-weight: bold;
                        color: #00ff41;
                    }
                    .metric-label {
                        font-size: 14px;
                        color: #888;
                        margin-top: 5px;
                    }
                    .connection-grid {
                        display: grid;
                        grid-template-columns: repeat(20, 1fr);
                        gap: 5px;
                        margin-top: 20px;
                    }
                    .connection-cell {
                        width: 30px;
                        height: 30px;
                        background: #1a1f3a;
                        border: 1px solid #00ff41;
                        border-radius: 3px;
                    }
                    .connection-cell.active {
                        background: #00ff41;
                    }
                    .connection-cell.timeout {
                        background: #ff0000;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>⚡ FLUX GATEWAY - HEARTBEAT MONITOR</h1>
                    <p>Real-time WebSocket Connection Status</p>
                </div>
                
                <div class="metrics">
                    <div class="metric-card">
                        <div class="metric-value" id="activeConnections">%d</div>
                        <div class="metric-label">Active Connections</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value" id="heartbeatsSent">0</div>
                        <div class="metric-label">Heartbeats Sent</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value" id="acksReceived">0</div>
                        <div class="metric-label">ACKs Received</div>
                    </div>
                </div>
                
                <h2>Connection Grid (First 100)</h2>
                <div class="connection-grid" id="grid"></div>
                
                <script>
                    // Initialize grid
                    const grid = document.getElementById('grid');
                    for (let i = 0; i < 100; i++) {
                        const cell = document.createElement('div');
                        cell.className = 'connection-cell';
                        cell.id = 'cell-' + i;
                        grid.appendChild(cell);
                    }
                    
                    // Update metrics every second
                    setInterval(async () => {
                        const response = await fetch('/api/metrics');
                        const data = await response.json();
                        
                        document.getElementById('activeConnections').textContent = data.activeConnections || 0;
                        document.getElementById('heartbeatsSent').textContent = data.heartbeatsSent || 0;
                        document.getElementById('acksReceived').textContent = data.acksReceived || 0;
                        
                        // Update connection grid based on active connections
                        const activeCount = data.activeConnections || 0;
                        for (let i = 0; i < 100; i++) {
                            const cell = document.getElementById('cell-' + i);
                            if (i < activeCount) {
                                cell.className = 'connection-cell active';
                            } else {
                                cell.className = 'connection-cell';
                            }
                        }
                    }, 1000);
                </script>
            </body>
            </html>
            """.formatted(
                gateway.getRegistry().getActiveCount()
            );
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
