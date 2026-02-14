package com.flux.gateway.server;

import com.flux.gateway.ring.ConsistentHashRing;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP dashboard for visualizing ring distribution.
 * Uses com.sun.net.httpserver (built into JDK, zero dependencies).
 */
public class DashboardServer {
    
    private final HttpServer server;
    private final ConsistentHashRing ring;
    private final Gson gson;
    
    public DashboardServer(int port, ConsistentHashRing ring) throws IOException {
        this.ring = ring;
        this.gson = new Gson();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Use virtual threads for request handling
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        
        // Register endpoints
        server.createContext("/", this::handleDashboard);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/add-server", this::handleAddServer);
        server.createContext("/api/remove-server", this::handleRemoveServer);
    }
    
    public void start() {
        server.start();
        System.out.println("📊 Dashboard server started on http://localhost:" + 
            server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = getDashboardHTML();
        sendResponse(exchange, 200, html, "text/html");
    }
    
    private void handleStats(HttpExchange exchange) throws IOException {
        var stats = ring.getStats();
        Map<String, Object> response = new HashMap<>();
        response.put("totalConnections", stats.totalConnections());
        response.put("distribution", stats.distribution());
        response.put("stdDev", stats.stdDev());
        response.put("variancePercent", stats.variancePercent());
        response.put("vnodeCount", ring.getVirtualNodeCount());
        String json = gson.toJson(response);
        sendResponse(exchange, 200, json, "application/json");
    }
    
    private void handleAddServer(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed", "text/plain");
            return;
        }
        
        // Parse request body to get server ID
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = gson.fromJson(body, Map.class);
        String serverId = params.get("serverId");
        
        if (serverId != null && !serverId.isBlank()) {
            ring.addServer(serverId);
            sendResponse(exchange, 200, "{\"status\":\"ok\"}", "application/json");
        } else {
            sendResponse(exchange, 400, "{\"error\":\"serverId required\"}", "application/json");
        }
    }
    
    private void handleRemoveServer(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed", "text/plain");
            return;
        }
        
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = gson.fromJson(body, Map.class);
        String serverId = params.get("serverId");
        
        if (serverId != null && !serverId.isBlank()) {
            ring.removeServer(serverId);
            sendResponse(exchange, 200, "{\"status\":\"ok\"}", "application/json");
        } else {
            sendResponse(exchange, 400, "{\"error\":\"serverId required\"}", "application/json");
        }
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) 
            throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private String getDashboardHTML() {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Flux - Virtual Node Ring Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', system-ui, sans-serif;
            background: linear-gradient(135deg, #ffb6c1 0%, #ffe4ec 100%);
            color: #fff;
            padding: 20px;
            min-height: 100vh;
        }
        .container {
            max-width: 1400px;
            margin: 0 auto;
        }
        h1 {
            font-size: 2.5rem;
            margin-bottom: 10px;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }
        .subtitle {
            font-size: 1rem;
            opacity: 0.9;
            margin-bottom: 30px;
        }
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .stat-card {
            background: rgba(255,255,255,0.1);
            backdrop-filter: blur(10px);
            border-radius: 12px;
            padding: 20px;
            border: 1px solid rgba(255,255,255,0.2);
        }
        .stat-label {
            font-size: 0.85rem;
            opacity: 0.8;
            margin-bottom: 8px;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        .stat-value {
            font-size: 2rem;
            font-weight: bold;
        }
        .chart-container {
            background: rgba(255,255,255,0.1);
            backdrop-filter: blur(10px);
            border-radius: 12px;
            padding: 30px;
            margin-bottom: 30px;
            border: 1px solid rgba(255,255,255,0.2);
        }
        .chart-title {
            font-size: 1.5rem;
            margin-bottom: 20px;
        }
        .bar-chart {
            display: flex;
            flex-direction: column;
            gap: 15px;
        }
        .bar-row {
            display: flex;
            align-items: center;
            gap: 15px;
        }
        .bar-label {
            min-width: 120px;
            font-weight: 500;
        }
        .bar-container {
            flex: 1;
            background: rgba(0,0,0,0.2);
            border-radius: 8px;
            height: 30px;
            position: relative;
            overflow: hidden;
        }
        .bar-fill {
            background: linear-gradient(90deg, #4ade80, #22c55e);
            height: 100%;
            border-radius: 8px;
            transition: width 0.5s ease;
            display: flex;
            align-items: center;
            justify-content: flex-end;
            padding-right: 10px;
            font-size: 0.85rem;
            font-weight: bold;
        }
        .controls {
            display: flex;
            gap: 15px;
            margin-bottom: 30px;
            flex-wrap: wrap;
        }
        .btn {
            background: rgba(255,255,255,0.2);
            border: 1px solid rgba(255,255,255,0.3);
            color: #fff;
            padding: 12px 24px;
            border-radius: 8px;
            cursor: pointer;
            font-size: 1rem;
            font-weight: 500;
            transition: all 0.3s;
            backdrop-filter: blur(10px);
        }
        .btn:hover {
            background: rgba(255,255,255,0.3);
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0,0,0,0.2);
        }
        .input-group {
            display: flex;
            gap: 10px;
            align-items: center;
        }
        .input-group input {
            background: rgba(255,255,255,0.1);
            border: 1px solid rgba(255,255,255,0.3);
            color: #fff;
            padding: 12px;
            border-radius: 8px;
            font-size: 1rem;
            width: 200px;
        }
        .input-group input::placeholder {
            color: rgba(255,255,255,0.6);
        }
        .variance-indicator {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 0.9rem;
            font-weight: bold;
            margin-left: 10px;
        }
        .variance-good { background: #22c55e; color: #fff; }
        .variance-ok { background: #eab308; color: #000; }
        .variance-bad { background: #ef4444; color: #fff; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🔮 Flux Virtual Node Ring</h1>
        <p class="subtitle">Day 50: Real-time Consistent Hashing Distribution</p>
        
        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-label">Total Connections</div>
                <div class="stat-value" id="totalConnections">0</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Active Servers</div>
                <div class="stat-value" id="serverCount">0</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Virtual Nodes</div>
                <div class="stat-value" id="vnodeCount">0</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Std Deviation</div>
                <div class="stat-value" id="stdDev">0</div>
            </div>
        </div>
        
        <div class="chart-container">
            <div class="chart-title">
                Connection Distribution
                <span class="variance-indicator" id="varianceIndicator">0% variance</span>
            </div>
            <div class="bar-chart" id="distributionChart"></div>
        </div>
        
        <div class="controls">
            <div class="input-group">
                <input type="text" id="serverIdInput" placeholder="gateway-11" />
                <button class="btn" onclick="addServer()">➕ Add Server</button>
                <button class="btn" onclick="removeServer()">➖ Remove Server</button>
            </div>
            <button class="btn" onclick="refreshStats()">🔄 Refresh</button>
        </div>
    </div>
    
    <script>
        let currentStats = null;
        
        async function fetchStats() {
            try {
                const response = await fetch('/api/stats');
                currentStats = await response.json();
                updateDashboard();
            } catch (error) {
                console.error('Failed to fetch stats:', error);
            }
        }
        
        function updateDashboard() {
            if (!currentStats) return;
            
            const distribution = currentStats.distribution || {};
            
            // Update stat cards
            document.getElementById('totalConnections').textContent = 
                (currentStats.totalConnections || 0).toLocaleString();
            
            const serverCount = Object.keys(distribution).length;
            document.getElementById('serverCount').textContent = serverCount;
            
            const vnodeCount = currentStats.vnodeCount != null ? currentStats.vnodeCount : (serverCount * 150);
            document.getElementById('vnodeCount').textContent = vnodeCount.toLocaleString();
            
            document.getElementById('stdDev').textContent = 
                Math.round(currentStats.stdDev || 0).toLocaleString();
            
            // Update variance indicator
            const varianceElem = document.getElementById('varianceIndicator');
            const variance = currentStats.variancePercent || 0;
            varianceElem.textContent = variance.toFixed(2) + '% variance';
            
            if (variance < 1) {
                varianceElem.className = 'variance-indicator variance-good';
            } else if (variance < 5) {
                varianceElem.className = 'variance-indicator variance-ok';
            } else {
                varianceElem.className = 'variance-indicator variance-bad';
            }
            
            // Update bar chart
            updateBarChart();
        }
        
        function updateBarChart() {
            const chartElem = document.getElementById('distributionChart');
            chartElem.innerHTML = '';
            
            const distribution = currentStats.distribution || {};
            const values = Object.values(distribution);
            const maxConnections = values.length > 0 ? Math.max(...values) : 0;
            
            const sortedServers = Object.keys(distribution).sort();
            
            sortedServers.forEach(server => {
                const count = distribution[server];
                const percentage = maxConnections > 0 ? (count / maxConnections) * 100 : 0;
                
                const row = document.createElement('div');
                row.className = 'bar-row';
                row.innerHTML = `
                    <div class="bar-label">${server}</div>
                    <div class="bar-container">
                        <div class="bar-fill" style="width: ${Math.max(percentage, 1)}%">
                            ${count.toLocaleString()}
                        </div>
                    </div>
                `;
                chartElem.appendChild(row);
            });
        }
        
        async function addServer() {
            const serverId = document.getElementById('serverIdInput').value.trim();
            if (!serverId) {
                alert('Please enter a server ID');
                return;
            }
            
            try {
                const response = await fetch('/api/add-server', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ serverId })
                });
                
                if (response.ok) {
                    document.getElementById('serverIdInput').value = '';
                    await fetchStats();
                }
            } catch (error) {
                console.error('Failed to add server:', error);
            }
        }
        
        async function removeServer() {
            const serverId = document.getElementById('serverIdInput').value.trim();
            if (!serverId) {
                alert('Please enter a server ID');
                return;
            }
            
            try {
                const response = await fetch('/api/remove-server', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ serverId })
                });
                
                if (response.ok) {
                    document.getElementById('serverIdInput').value = '';
                    await fetchStats();
                }
            } catch (error) {
                console.error('Failed to remove server:', error);
            }
        }
        
        function refreshStats() {
            fetchStats();
        }
        
        // Auto-refresh every 2 seconds
        setInterval(fetchStats, 2000);
        
        // Initial load
        fetchStats();
    </script>
</body>
</html>
        """;
    }
}
