package com.flux.loadbalancer.dashboard;

import com.flux.loadbalancer.models.GatewayNode;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class DashboardServer {
    private static final String REGISTRY_KEY = "gateway:nodes";
    private final JedisPool jedisPool;
    private final int port;
    private final Gson gson = new Gson();
    
    public DashboardServer(JedisPool jedisPool, int port) {
        this.jedisPool = jedisPool;
        this.port = port;
    }
    
    public void start() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Redirect root to dashboard
        server.createContext("/", exchange -> {
            if ("/".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().set("Location", "/dashboard");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
        });
        
        // Dashboard HTML page (use byte length for Content-Length so UTF-8 is not truncated)
        server.createContext("/dashboard", exchange -> {
            var html = generateDashboardHtml();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        
        // API endpoint for cluster status (serialize as maps so Instant becomes epoch seconds)
        server.createContext("/api/cluster", exchange -> {
            try {
                var nodes = fetchGatewayNodes();
                var list = new java.util.ArrayList<java.util.Map<String, Object>>();
                for (var node : nodes) {
                    var map = new java.util.LinkedHashMap<String, Object>();
                    map.put("nodeId", node.nodeId());
                    map.put("ipAddress", node.ipAddress());
                    map.put("port", node.port());
                    map.put("currentConnections", node.currentConnections());
                    map.put("lastHeartbeat", node.lastHeartbeat().getEpochSecond());
                    map.put("status", node.status());
                    list.add(map);
                }
                var json = gson.toJson(list);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
                exchange.getResponseHeaders().set("Expires", "0");
                exchange.getResponseHeaders().set("Pragma", "no-cache");
                exchange.sendResponseHeaders(200, json.length());
                try (var os = exchange.getResponseBody()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                System.err.println("[Dashboard] /api/cluster error: " + e.getClass().getName() + " " + e.getMessage());
                try {
                    var err = "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, err.length());
                    exchange.getResponseBody().write(err.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {}
            }
        });
        
        // API: Kill gateway (immediate stop)
        server.createContext("/api/kill", exchange -> handleDockerAction(exchange, "kill"));
        // API: Drain gateway (graceful SIGTERM)
        server.createContext("/api/drain", exchange -> handleDockerAction(exchange, "drain"));
        
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        
        System.out.println("[Dashboard] Started on port " + port);
    }
    
    private List<GatewayNode> fetchGatewayNodes() {
        var nodes = new ArrayList<GatewayNode>();
        
        try (var jedis = jedisPool.getResource()) {
            var entries = jedis.hgetAll(REGISTRY_KEY);
            
            entries.forEach((nodeId, json) -> {
                try {
                    var data = gson.fromJson(json, java.util.Map.class);
                    var node = new GatewayNode(
                        (String) data.get("nodeId"),
                        (String) data.get("ipAddress"),
                        numInt(data.get("port")),
                        numInt(data.get("currentConnections")),
                        Instant.ofEpochSecond(numLong(data.get("lastHeartbeat"))),
                        (String) data.get("status")
                    );
                    nodes.add(node);
                } catch (Exception e) {
                    System.err.println("[Dashboard] Error parsing node data: " + e.getMessage());
                }
            });
        }
        
        return nodes;
    }
    
    private static int numInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
    
    private static long numLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
    
    private static final Set<String> VALID_GATEWAYS = Set.of("gateway-1", "gateway-2", "gateway-3");
    
    private void handleDockerAction(com.sun.net.httpserver.HttpExchange exchange, String action) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}");
            return;
        }
        var path = exchange.getRequestURI().getPath();
        var parts = path.split("/");
        var gatewayId = parts.length >= 4 ? parts[3] : "";
        if (!VALID_GATEWAYS.contains(gatewayId)) {
            sendJson(exchange, 400, "{\"error\":\"Invalid gateway. Use gateway-1, gateway-2, or gateway-3\"}");
            return;
        }
        var containerName = "flux-" + gatewayId;
        ProcessBuilder pb;
        if ("kill".equals(action)) {
            pb = new ProcessBuilder("docker", "kill", containerName);
        } else {
            pb = new ProcessBuilder("docker", "stop", containerName);
        }
        pb.redirectErrorStream(true);
        try {
            var proc = pb.start();
            proc.waitFor();
            var msg = "drain".equals(action)
                ? "Graceful drain initiated for " + gatewayId + ". Dashboard will update in ~15s."
                : "Gateway " + gatewayId + " killed. Dashboard will update in ~15s.";
            sendJson(exchange, 200, "{\"ok\":true,\"message\":\"" + msg.replace("\"", "'") + "\"}");
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
    
    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int code, String json) throws IOException {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    
    private String generateDashboardHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Flux Gateway Cluster Dashboard</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        
        .container {
            max-width: 1400px;
            margin: 0 auto;
        }
        
        header {
            background: white;
            border-radius: 10px;
            padding: 30px;
            margin-bottom: 30px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
        }
        
        h1 {
            color: #333;
            font-size: 32px;
            margin-bottom: 10px;
        }
        
        .subtitle {
            color: #666;
            font-size: 16px;
        }
        
        .stats {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        
        .stat-card {
            background: white;
            border-radius: 10px;
            padding: 25px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
        }
        
        .stat-label {
            color: #666;
            font-size: 14px;
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-bottom: 10px;
        }
        
        .stat-value {
            color: #333;
            font-size: 36px;
            font-weight: bold;
        }
        
        .gateway-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        
        .gateway-card {
            background: white;
            border-radius: 10px;
            padding: 25px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
            position: relative;
            overflow: hidden;
        }
        
        .gateway-card.healthy {
            border-left: 5px solid #10b981;
        }
        
        .gateway-card.draining {
            border-left: 5px solid #f59e0b;
        }
        
        .gateway-card.unhealthy {
            border-left: 5px solid #ef4444;
        }
        
        .gateway-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
        }
        
        .gateway-name {
            font-size: 20px;
            font-weight: bold;
            color: #333;
        }
        
        .status-badge {
            padding: 5px 15px;
            border-radius: 20px;
            font-size: 12px;
            font-weight: bold;
            text-transform: uppercase;
        }
        
        .status-badge.healthy {
            background: #d1fae5;
            color: #065f46;
        }
        
        .status-badge.draining {
            background: #fef3c7;
            color: #92400e;
        }
        
        .status-badge.unhealthy {
            background: #fee2e2;
            color: #991b1b;
        }
        
        .gateway-info {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 15px;
            margin-bottom: 20px;
        }
        
        .info-item {
            display: flex;
            flex-direction: column;
        }
        
        .info-label {
            font-size: 12px;
            color: #666;
            margin-bottom: 5px;
        }
        
        .info-value {
            font-size: 16px;
            color: #333;
            font-weight: 500;
        }
        
        .connection-bar {
            width: 100%;
            height: 8px;
            background: #e5e7eb;
            border-radius: 4px;
            overflow: hidden;
            margin-top: 10px;
        }
        
        .connection-fill {
            height: 100%;
            background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
            transition: width 0.3s ease;
        }
        
        .controls {
            background: white;
            border-radius: 10px;
            padding: 25px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
        }
        
        .controls h2 {
            color: #333;
            margin-bottom: 15px;
        }
        
        .button-group {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
        }
        
        button {
            padding: 12px 24px;
            border: none;
            border-radius: 6px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
        }
        
        .btn-danger {
            background: #ef4444;
            color: white;
        }
        
        .btn-danger:hover {
            background: #dc2626;
            transform: translateY(-2px);
            box-shadow: 0 4px 8px rgba(239, 68, 68, 0.3);
        }
        
        .btn-warning {
            background: #f59e0b;
            color: white;
        }
        
        .btn-warning:hover {
            background: #d97706;
            transform: translateY(-2px);
            box-shadow: 0 4px 8px rgba(245, 158, 11, 0.3);
        }
        
        .btn-primary {
            background: #667eea;
            color: white;
        }
        
        .btn-primary:hover {
            background: #5568d3;
            transform: translateY(-2px);
            box-shadow: 0 4px 8px rgba(102, 126, 234, 0.3);
        }
        
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        
        .loading {
            animation: pulse 2s infinite;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>🚀 Flux Gateway Cluster</h1>
            <p class="subtitle">Real-time monitoring and control for distributed WebSocket gateways</p>
            <p class="subtitle" style="font-size:12px; margin-top:8px;"><span id="lastUpdated">-</span></p>
        </header>
        
        <div class="stats" id="stats">
            <div class="stat-card">
                <div class="stat-label">Total Gateways</div>
                <div class="stat-value" id="totalGateways">-</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Healthy Nodes</div>
                <div class="stat-value" id="healthyNodes">-</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Total Connections</div>
                <div class="stat-value" id="totalConnections">-</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Avg Connections/Node</div>
                <div class="stat-value" id="avgConnections">-</div>
            </div>
        </div>
        
        <div class="gateway-grid" id="gatewayGrid"></div>
        
        <div class="controls">
            <h2>Simulation Controls</h2>
            <div class="button-group">
                <button class="btn-danger" onclick="killGateway('gateway-1')">⚠️ Kill Gateway 1</button>
                <button class="btn-danger" onclick="killGateway('gateway-2')">⚠️ Kill Gateway 2</button>
                <button class="btn-danger" onclick="killGateway('gateway-3')">⚠️ Kill Gateway 3</button>
                <button class="btn-warning" onclick="drainGateway('gateway-1')">🔄 Drain Gateway 1</button>
                <button class="btn-warning" onclick="drainGateway('gateway-2')">🔄 Drain Gateway 2</button>
                <button class="btn-warning" onclick="drainGateway('gateway-3')">🔄 Drain Gateway 3</button>
                <button class="btn-primary" onclick="refreshData()">🔄 Refresh Data</button>
            </div>
            <p id="actionFeedback" style="margin-top:12px; font-size:14px; color:#333;"></p>
        </div>
    </div>
    
    <script>
        let maxConnections = 5000;
        const POLL_MS = 1000;
        
        async function fetchClusterData() {
            try {
                var r = await fetch('/api/cluster?_=' + Date.now(), { cache: 'no-store', method: 'GET' });
                if (!r.ok) throw new Error(r.status);
                var nodes = await r.json();
                var el = document.getElementById('lastUpdated');
                if (el) el.textContent = 'Last updated: ' + new Date().toLocaleTimeString() + ' (live)';
                updateDashboard(Array.isArray(nodes) ? nodes : []);
            } catch (e) {
                var el = document.getElementById('lastUpdated');
                if (el) el.textContent = 'Error: ' + e.message + ' - retrying...';
            }
            setTimeout(fetchClusterData, POLL_MS);
        }
        
        const HEARTBEAT_STALE_SEC = 12;  // Treat as dead if no heartbeat within this (gateway sends every 5s)
        
        function isEffectivelyHealthy(node, now) {
            const age = now - (node.lastHeartbeat || 0);
            return node.status === 'HEALTHY' && age >= 0 && age <= HEARTBEAT_STALE_SEC;
        }
        
        function updateDashboard(nodes) {
            const now = Date.now() / 1000;
            const totalGateways = nodes.length;
            const healthyNodes = nodes.filter(n => isEffectivelyHealthy(n, now)).length;
            const totalConnections = nodes.reduce((sum, n) => sum + n.currentConnections, 0);
            const avgConnections = healthyNodes > 0 ? Math.round(totalConnections / healthyNodes) : 0;
            
            document.getElementById('totalGateways').textContent = totalGateways;
            document.getElementById('healthyNodes').textContent = healthyNodes;
            document.getElementById('totalConnections').textContent = totalConnections.toLocaleString();
            document.getElementById('avgConnections').textContent = avgConnections.toLocaleString();
            
            // Update max connections for bar calculation
            maxConnections = Math.max(maxConnections, ...nodes.map(n => n.currentConnections), 1000);
            
            // Update gateway cards
            const grid = document.getElementById('gatewayGrid');
            grid.innerHTML = nodes.map(node => createGatewayCard(node, now)).join('');
        }
        
        function createGatewayCard(node, now) {
            const heartbeatAge = Math.max(0, Math.round(now - (node.lastHeartbeat || 0)));
            const isStale = heartbeatAge > HEARTBEAT_STALE_SEC;
            const isHealthy = !isStale && node.status === 'HEALTHY';
            const isDraining = !isStale && node.status === 'DRAINING';
            const statusClass = isHealthy ? 'healthy' : (isDraining ? 'draining' : 'unhealthy');
            const effectiveStatus = isStale ? 'OFFLINE' : node.status;
            const connectionPercent = (node.currentConnections / maxConnections) * 100;
            
            return `
                <div class="gateway-card ${statusClass}">
                    <div class="gateway-header">
                        <div class="gateway-name">${node.nodeId}</div>
                        <div class="status-badge ${statusClass}">${effectiveStatus}</div>
                    </div>
                    <div class="gateway-info">
                        <div class="info-item">
                            <div class="info-label">IP Address</div>
                            <div class="info-value">${node.ipAddress}</div>
                        </div>
                        <div class="info-item">
                            <div class="info-label">Port</div>
                            <div class="info-value">${node.port}</div>
                        </div>
                        <div class="info-item">
                            <div class="info-label">Connections</div>
                            <div class="info-value">${node.currentConnections.toLocaleString()}</div>
                        </div>
                        <div class="info-item">
                            <div class="info-label">Last Heartbeat</div>
                            <div class="info-value">${heartbeatAge}s ago</div>
                        </div>
                    </div>
                    <div class="connection-bar">
                        <div class="connection-fill" style="width: ${connectionPercent}%"></div>
                    </div>
                </div>
            `;
        }
        
        async function killGateway(gatewayId) {
            if (!confirm(`Kill ${gatewayId}? This will immediately stop the gateway.`)) return;
            await callAction('kill', gatewayId);
        }
        
        async function drainGateway(gatewayId) {
            if (!confirm(`Drain ${gatewayId}? This will gracefully shut it down (connections drain first).`)) return;
            await callAction('drain', gatewayId);
        }
        
        async function callAction(action, gatewayId) {
            var fb = document.getElementById('actionFeedback');
            fb.textContent = action + 'ing ' + gatewayId + '...';
            try {
                var r = await fetch('/api/' + action + '/' + gatewayId, { method: 'POST', cache: 'no-store' });
                var data = await r.json();
                fb.textContent = data.message || data.error || (r.ok ? 'Done.' : 'Failed.');
                fb.style.color = r.ok ? '#059669' : '#dc2626';
            } catch (e) {
                fb.textContent = 'Error: ' + e.message;
                fb.style.color = '#dc2626';
            }
            setTimeout(function(){ fb.textContent = ''; }, 5000);
        }
        
        function refreshData() {
            fetchClusterData();
        }
        
        // Start real-time polling (recursive setTimeout so every request is fresh)
        fetchClusterData();
    </script>
</body>
</html>
        """;
    }
}
