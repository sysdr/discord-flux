package com.flux.dashboard;

import com.flux.gateway.FluxGateway;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class DashboardServer {
    private final int port;
    private final FluxGateway gateway;
    private final HttpServer server;
    
    public DashboardServer(int port, FluxGateway gateway) throws IOException {
        this.port = port;
        this.gateway = gateway;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/dashboard", this::handleDashboard);
        server.createContext("/api/metrics", this::handleMetrics);
        server.setExecutor(null);
    }
    
    public void start() {
        server.start();
        System.out.println("📊 Dashboard server started on port " + port);
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = generateDashboardHTML();
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
    
    private void handleMetrics(HttpExchange exchange) throws IOException {
        var metrics = gateway.metrics();
        String json = String.format(
            "{\"active\":%d,\"total\":%d,\"handshakes\":%d,\"errors\":%d}",
            gateway.activeConnections(),
            metrics.totalConnections(),
            metrics.totalHandshakes(),
            metrics.handshakeErrors()
        );
        
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
    
    private String generateDashboardHTML() {
        return """
<!DOCTYPE html>
<html>
<head>
    <title>Flux Gateway - Day 1 Dashboard</title>
    <style>
        body { font-family: monospace; background: #1a1a1a; color: #00ff00; padding: 20px; }
        .container { max-width: 1200px; margin: 0 auto; }
        .metric-card { background: #2a2a2a; border: 2px solid #00ff00; padding: 20px; margin: 10px 0; }
        .metric-value { font-size: 48px; font-weight: bold; color: #00ff00; }
        .metric-label { font-size: 14px; color: #888; }
        .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; }
        h1 { border-bottom: 2px solid #00ff00; padding-bottom: 10px; }
        .status { color: #00ff00; }
    </style>
</head>
<body>
    <div class="container">
        <h1>⚡ FLUX GATEWAY - DAY 1: HANDSHAKE MONITORING</h1>
        
        <div class="grid">
            <div class="metric-card">
                <div class="metric-label">ACTIVE CONNECTIONS</div>
                <div class="metric-value" id="active">0</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">TOTAL CONNECTIONS</div>
                <div class="metric-value" id="total">0</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">COMPLETED HANDSHAKES</div>
                <div class="metric-value" id="handshakes">0</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">ERRORS</div>
                <div class="metric-value" id="errors">0</div>
            </div>
        </div>
        
        <div class="metric-card" style="margin-top: 30px;">
            <h3>🔍 SYSTEM STATUS</h3>
            <div class="status" id="status">● Gateway Running - Selector Active</div>
        </div>
    </div>
    
    <script>
        async function updateMetrics() {
            try {
                const response = await fetch('/api/metrics');
                const data = await response.json();
                
                document.getElementById('active').textContent = data.active;
                document.getElementById('total').textContent = data.total;
                document.getElementById('handshakes').textContent = data.handshakes;
                document.getElementById('errors').textContent = data.errors;
            } catch (e) {
                console.error('Failed to fetch metrics:', e);
            }
        }
        
        setInterval(updateMetrics, 1000);
        updateMetrics();
    </script>
</body>
</html>
        """;
    }
}
