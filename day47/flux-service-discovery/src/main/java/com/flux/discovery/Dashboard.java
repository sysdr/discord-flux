package com.flux.discovery;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight HTTP dashboard for visualizing service discovery.
 */
public class Dashboard {
    
    private static final Logger log = LoggerFactory.getLogger(Dashboard.class);
    private static final int PORT = 8080;
    
    private final ServiceRegistry registry;
    private final GatewaySimulator simulator;
    private final Gson gson;
    private HttpServer server;
    
    public Dashboard(ServiceRegistry registry, GatewaySimulator simulator) {
        this.registry = registry;
        this.simulator = simulator;
        this.gson = new Gson();
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Main dashboard page (use byte length so Content-Length matches UTF-8 body)
        server.createContext("/", exchange -> {
            String html = generateDashboardHTML();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        
        // API: Get all nodes (serialize without ByteBuffer - Gson cannot serialize it)
        server.createContext("/api/nodes", exchange -> {
            List<ServiceNode> nodes = registry.discover();
            List<Map<String, Object>> safe = new java.util.ArrayList<>();
            for (ServiceNode n : nodes) {
                safe.add(Map.of(
                    "id", n.id(),
                    "host", n.host(),
                    "port", n.port(),
                    "registeredAt", n.registeredAt(),
                    "status", n.status().name()
                ));
            }
            String json = gson.toJson(safe);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        
        // API: Get metrics
        server.createContext("/api/metrics", exchange -> {
            RegistryMetrics metrics = registry.getMetrics();
            String json = gson.toJson(metrics);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        
        // API: Simulate crash
        server.createContext("/api/simulate/crash", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                simulator.simulateCrashes(5);
                String response = "{\"status\":\"ok\"}";
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        
        // API: Simulate storm
        server.createContext("/api/simulate/storm", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                Thread.startVirtualThread(() -> simulator.simulateRegistrationStorm(100));
                String response = "{\"status\":\"ok\"}";
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        
        server.setExecutor(null); // Use default executor
        server.start();
        
        log.info("Dashboard started at http://localhost:{}", PORT);
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("Dashboard stopped");
        }
    }
    
    private String generateDashboardHTML() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Flux Service Discovery Dashboard</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: 'Courier New', monospace;
                        background: #0a0e27;
                        color: #00ff00;
                        padding: 20px;
                    }
                    
                    .container {
                        max-width: 1400px;
                        margin: 0 auto;
                    }
                    
                    h1 {
                        text-align: center;
                        font-size: 2em;
                        margin-bottom: 10px;
                        text-shadow: 0 0 10px #00ff00;
                    }
                    
                    .subtitle {
                        text-align: center;
                        color: #00aa00;
                        margin-bottom: 30px;
                    }
                    
                    .metrics {
                        display: grid;
                        grid-template-columns: repeat(4, 1fr);
                        gap: 15px;
                        margin-bottom: 30px;
                    }
                    
                    .metric-card {
                        background: #0f1629;
                        border: 2px solid #00ff00;
                        padding: 20px;
                        border-radius: 5px;
                        text-align: center;
                    }
                    
                    .metric-value {
                        font-size: 2.5em;
                        font-weight: bold;
                        margin: 10px 0;
                    }
                    
                    .metric-label {
                        color: #00aa00;
                        font-size: 0.9em;
                    }
                    
                    .controls {
                        display: flex;
                        gap: 15px;
                        margin-bottom: 30px;
                        flex-wrap: wrap;
                    }
                    
                    button {
                        background: #00ff00;
                        color: #0a0e27;
                        border: none;
                        padding: 12px 24px;
                        font-family: 'Courier New', monospace;
                        font-size: 1em;
                        font-weight: bold;
                        cursor: pointer;
                        border-radius: 5px;
                        transition: all 0.3s;
                    }
                    
                    button:hover {
                        background: #00ff00;
                        box-shadow: 0 0 20px #00ff00;
                        transform: translateY(-2px);
                    }
                    
                    button:active {
                        transform: translateY(0);
                    }
                    
                    .nodes-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
                        gap: 15px;
                        margin-bottom: 30px;
                    }
                    
                    .node-card {
                        background: #0f1629;
                        border: 2px solid #00ff00;
                        padding: 15px;
                        border-radius: 5px;
                        transition: all 0.3s;
                        animation: fadeIn 0.5s;
                    }
                    
                    @keyframes fadeIn {
                        from { opacity: 0; transform: scale(0.9); }
                        to { opacity: 1; transform: scale(1); }
                    }
                    
                    .node-card:hover {
                        box-shadow: 0 0 20px #00ff00;
                        transform: translateY(-5px);
                    }
                    
                    .node-id {
                        font-weight: bold;
                        font-size: 1.1em;
                        margin-bottom: 8px;
                    }
                    
                    .node-detail {
                        color: #00aa00;
                        font-size: 0.85em;
                        margin: 4px 0;
                    }
                    
                    .node-status {
                        display: inline-block;
                        padding: 3px 8px;
                        border-radius: 3px;
                        font-size: 0.8em;
                        margin-top: 8px;
                    }
                    
                    .status-healthy {
                        background: #00ff00;
                        color: #0a0e27;
                    }
                    
                    .status-suspected {
                        background: #ffaa00;
                        color: #0a0e27;
                    }
                    
                    .status-dead {
                        background: #ff0000;
                        color: white;
                    }
                    
                    .log {
                        background: #0f1629;
                        border: 2px solid #00ff00;
                        padding: 15px;
                        border-radius: 5px;
                        height: 200px;
                        overflow-y: auto;
                        font-size: 0.85em;
                    }
                    
                    .log-entry {
                        margin: 5px 0;
                        padding: 5px;
                        border-left: 3px solid #00ff00;
                        padding-left: 10px;
                    }
                    
                    .timestamp {
                        color: #00aa00;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>⚡ FLUX SERVICE DISCOVERY</h1>
                    <div class="subtitle">Real-time Gateway Node Registry | Powered by Redis + Virtual Threads</div>
                    
                    <div class="metrics">
                        <div class="metric-card">
                            <div class="metric-label">ACTIVE NODES</div>
                            <div class="metric-value" id="node-count">0</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-label">REGISTRATIONS</div>
                            <div class="metric-value" id="registrations">0</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-label">HEARTBEAT SUCCESS</div>
                            <div class="metric-value" id="heartbeat-success">0</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-label">HEARTBEAT FAILURES</div>
                            <div class="metric-value" id="heartbeat-failures">0</div>
                        </div>
                    </div>
                    
                    <div class="controls">
                        <button onclick="refreshData()">🔄 Refresh</button>
                        <button onclick="simulateCrash()">💥 Simulate Crash (5 nodes)</button>
                        <button onclick="simulateStorm()">⛈️ Registration Storm (100 nodes)</button>
                    </div>
                    
                    <h2 style="margin-bottom: 15px;">📡 Active Gateway Nodes</h2>
                    <div class="nodes-grid" id="nodes-grid"></div>
                    
                    <h2 style="margin-bottom: 15px;">📋 Event Log</h2>
                    <div class="log" id="event-log"></div>
                </div>
                
                <script>
                    let logs = [];
                    
                    function addLog(message) {
                        const timestamp = new Date().toLocaleTimeString();
                        logs.unshift({ timestamp, message });
                        if (logs.length > 20) logs.pop();
                        
                        const logDiv = document.getElementById('event-log');
                        logDiv.innerHTML = logs.map(log => 
                            `<div class="log-entry"><span class="timestamp">[${log.timestamp}]</span> ${log.message}</div>`
                        ).join('');
                    }
                    
                    async function refreshData() {
                        try {
                            // Fetch nodes
                            const nodesResp = await fetch('/api/nodes');
                            const nodes = await nodesResp.json();
                            
                            // Fetch metrics
                            const metricsResp = await fetch('/api/metrics');
                            const metrics = await metricsResp.json();
                            
                            // Update metrics
                            document.getElementById('node-count').textContent = nodes.length;
                            document.getElementById('registrations').textContent = metrics.registrations;
                            document.getElementById('heartbeat-success').textContent = metrics.heartbeatSuccesses;
                            document.getElementById('heartbeat-failures').textContent = metrics.heartbeatFailures;
                            
                            // Update nodes grid
                            const grid = document.getElementById('nodes-grid');
                            grid.innerHTML = nodes.map(node => `
                                <div class="node-card">
                                    <div class="node-id">${node.id}</div>
                                    <div class="node-detail">🌐 ${node.host}:${node.port}</div>
                                    <div class="node-detail">⏱️ Age: ${Math.floor((Date.now() - node.registeredAt) / 1000)}s</div>
                                    <span class="node-status status-${node.status.toLowerCase()}">${node.status}</span>
                                </div>
                            `).join('');
                            
                        } catch (error) {
                            addLog('❌ Error fetching data: ' + error.message);
                        }
                    }
                    
                    async function simulateCrash() {
                        try {
                            await fetch('/api/simulate/crash', { method: 'POST' });
                            addLog('💥 Simulated 5 node crashes');
                            setTimeout(refreshData, 500);
                        } catch (error) {
                            addLog('❌ Crash simulation failed: ' + error.message);
                        }
                    }
                    
                    async function simulateStorm() {
                        try {
                            await fetch('/api/simulate/storm', { method: 'POST' });
                            addLog('⛈️ Started registration storm (100 nodes)');
                            setTimeout(refreshData, 2000);
                        } catch (error) {
                            addLog('❌ Storm simulation failed: ' + error.message);
                        }
                    }
                    
                    // Auto-refresh every 2 seconds
                    setInterval(refreshData, 2000);
                    
                    // Initial load
                    refreshData();
                    addLog('✅ Dashboard initialized');
                </script>
            </body>
            </html>
            """;
    }
}
