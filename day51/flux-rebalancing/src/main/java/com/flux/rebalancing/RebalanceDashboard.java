package com.flux.rebalancing;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lightweight HTTP server serving real-time dashboard.
 * Uses built-in com.sun.net.httpserver (no external dependencies).
 */
public class RebalanceDashboard {
    
    private final RebalancingSimulator simulator;
    private HttpServer server;
    
    public RebalanceDashboard(RebalancingSimulator simulator) {
        this.simulator = simulator;
    }
    
    public void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            server.createContext("/", this::handleDashboard);
            server.createContext("/api/stats", this::handleStats);
            server.createContext("/api/add-node", this::handleAddNode);
            server.createContext("/api/events", this::handleEvents);
            
            server.setExecutor(null);
            server.start();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to start dashboard server", e);
        }
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = generateDashboardHTML();
        sendResponse(exchange, 200, html, "text/html");
    }
    
    private void handleStats(HttpExchange exchange) throws IOException {
        var stats = buildStatsJSON();
        sendResponse(exchange, 200, stats, "application/json");
    }
    
    private void handleAddNode(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}", "application/json");
            return;
        }
        
        String nodeId = "node-" + (simulator.getRing().getPhysicalNodeCount() + 1);
        
        // Trigger async migration
        simulator.addNode(nodeId)
            .thenAccept(result -> {
                System.out.println("[Dashboard] Node addition triggered: " + nodeId);
            });
        
        String response = String.format("{\"status\": \"ok\", \"nodeId\": \"%s\"}", nodeId);
        sendResponse(exchange, 200, response, "application/json");
    }
    
    private void handleEvents(HttpExchange exchange) throws IOException {
        var events = simulator.getEventLog().stream()
            .limit(50)
            .map(e -> String.format(
                "{\"timestamp\": \"%s\", \"type\": \"%s\", \"message\": \"%s\"}",
                e.timestamp(), e.type(), e.message().replace("\"", "\\\"")
            ))
            .collect(Collectors.joining(","));
        
        String json = "[" + events + "]";
        sendResponse(exchange, 200, json, "application/json");
    }
    
    private String buildStatsJSON() {
        var connections = simulator.getActiveConnections();
        var distribution = connections.values().stream()
            .collect(Collectors.groupingBy(
                GatewayNode::nodeId,
                Collectors.counting()
            ));
        
        var nodeStats = distribution.entrySet().stream()
            .map(e -> String.format(
                "{\"nodeId\": \"%s\", \"connections\": %d}",
                e.getKey(), e.getValue()
            ))
            .collect(Collectors.joining(","));
        
        double variance = connections.isEmpty() ? 0.0
            : simulator.getRing().calculateLoadVariance(connections);
        
        return String.format(
            "{\"totalConnections\": %d, \"nodeCount\": %d, \"variance\": %.4f, \"nodes\": [%s]}",
            connections.size(),
            simulator.getRing().getPhysicalNodeCount(),
            variance,
            nodeStats
        );
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String body, String contentType) 
        throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private String generateDashboardHTML() {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Flux Gateway - Rebalancing Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #fff;
            padding: 20px;
        }
        .container { max-width: 1400px; margin: 0 auto; }
        h1 {
            font-size: 2.5em;
            margin-bottom: 10px;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }
        .subtitle {
            font-size: 1.1em;
            opacity: 0.9;
            margin-bottom: 30px;
        }
        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
            gap: 20px;
            margin-bottom: 20px;
        }
        .card {
            background: rgba(255, 255, 255, 0.1);
            border-radius: 12px;
            padding: 25px;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.2);
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
        }
        .card h2 {
            font-size: 1.3em;
            margin-bottom: 15px;
            border-bottom: 2px solid rgba(255, 255, 255, 0.3);
            padding-bottom: 10px;
        }
        .metric {
            display: flex;
            justify-content: space-between;
            margin: 12px 0;
            font-size: 1.1em;
        }
        .metric-value {
            font-weight: bold;
            color: #ffd700;
        }
        .bar-chart {
            margin-top: 15px;
        }
        .bar {
            display: flex;
            align-items: center;
            margin: 10px 0;
        }
        .bar-label {
            width: 100px;
            font-size: 0.9em;
        }
        .bar-container {
            flex: 1;
            height: 25px;
            background: rgba(255, 255, 255, 0.2);
            border-radius: 12px;
            overflow: hidden;
            margin: 0 10px;
        }
        .bar-fill {
            height: 100%;
            background: linear-gradient(90deg, #36d1dc, #5b86e5);
            transition: width 0.5s ease;
            display: flex;
            align-items: center;
            justify-content: flex-end;
            padding-right: 8px;
            font-size: 0.85em;
        }
        button {
            background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
            color: white;
            border: none;
            padding: 12px 24px;
            font-size: 1em;
            border-radius: 8px;
            cursor: pointer;
            margin: 5px;
            transition: transform 0.2s, box-shadow 0.2s;
            font-weight: 600;
        }
        button:hover {
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(245, 87, 108, 0.4);
        }
        button:active {
            transform: translateY(0);
        }
        .controls {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
        }
        .event-log {
            max-height: 300px;
            overflow-y: auto;
            font-size: 0.9em;
            line-height: 1.6;
        }
        .event {
            padding: 8px;
            margin: 5px 0;
            background: rgba(255, 255, 255, 0.05);
            border-radius: 6px;
            border-left: 3px solid #ffd700;
        }
        .event-type {
            font-weight: bold;
            color: #ffd700;
        }
        .ring-viz {
            width: 100%;
            height: 300px;
            position: relative;
        }
        .status-good { color: #4ade80; }
        .status-warning { color: #fbbf24; }
        .status-bad { color: #f87171; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 Flux Gateway Rebalancing</h1>
        <div class="subtitle">Consistent Hash Ring with Virtual Nodes - Live Monitoring</div>
        
        <div class="grid">
            <div class="card">
                <h2>📊 Cluster Metrics</h2>
                <div class="metric">
                    <span>Total Connections:</span>
                    <span class="metric-value" id="total-connections">-</span>
                </div>
                <div class="metric">
                    <span>Gateway Nodes:</span>
                    <span class="metric-value" id="node-count">-</span>
                </div>
                <div class="metric">
                    <span>Load Variance (CV):</span>
                    <span class="metric-value" id="variance">-</span>
                </div>
                <div class="metric">
                    <span>Distribution Quality:</span>
                    <span class="metric-value" id="quality">-</span>
                </div>
            </div>
            
            <div class="card">
                <h2>⚙️ Control Panel</h2>
                <div class="controls">
                    <button onclick="addNode()">➕ Add Node</button>
                    <button onclick="refreshStats()">🔄 Refresh Stats</button>
                    <button onclick="clearEvents()">🗑️ Clear Events</button>
                </div>
                <div style="margin-top: 20px; font-size: 0.9em; opacity: 0.8;">
                    <p>💡 Click "Add Node" to trigger rebalancing</p>
                    <p>🎯 Target variance: &lt; 0.05 for optimal balance</p>
                </div>
            </div>
        </div>
        
        <div class="grid">
            <div class="card">
                <h2>📈 Load Distribution</h2>
                <div class="bar-chart" id="distribution-chart"></div>
            </div>
            
            <div class="card">
                <h2>📜 Event Log</h2>
                <div class="event-log" id="event-log"></div>
            </div>
        </div>
    </div>
    
    <script>
        let refreshInterval;
        
        async function addNode() {
            try {
                const response = await fetch('/api/add-node', { method: 'POST' });
                const data = await response.json();
                console.log('Node addition triggered:', data);
                await refreshStats();
            } catch (error) {
                console.error('Error adding node:', error);
            }
        }
        
        async function refreshStats() {
            try {
                const response = await fetch('/api/stats');
                const data = await response.json();
                updateDashboard(data);
            } catch (error) {
                console.error('Error fetching stats:', error);
            }
        }
        
        async function refreshEvents() {
            try {
                const response = await fetch('/api/events');
                const events = await response.json();
                updateEventLog(events);
            } catch (error) {
                console.error('Error fetching events:', error);
            }
        }
        
        function updateDashboard(data) {
            document.getElementById('total-connections').textContent = 
                (data.totalConnections ?? 0).toLocaleString();
            document.getElementById('node-count').textContent = data.nodeCount ?? 0;
            
            const v = Number.isFinite(data.variance) ? data.variance : 0;
            const varianceElem = document.getElementById('variance');
            varianceElem.textContent = v.toFixed(4);
            varianceElem.className = 'metric-value ' + 
                (v < 0.05 ? 'status-good' : 
                 v < 0.10 ? 'status-warning' : 'status-bad');
            
            const qualityElem = document.getElementById('quality');
            const quality = v < 0.05 ? 'Excellent' : 
                           v < 0.10 ? 'Good' : 'Poor';
            qualityElem.textContent = quality;
            qualityElem.className = 'metric-value ' + 
                (v < 0.05 ? 'status-good' : 
                 v < 0.10 ? 'status-warning' : 'status-bad');
            
            updateDistributionChart(data.nodes || [], data.totalConnections || 0);
        }
        
        function updateDistributionChart(nodes, total) {
            const chart = document.getElementById('distribution-chart');
            chart.innerHTML = '';
            const safeTotal = total || 1;
            nodes.forEach(node => {
                const percentage = (node.connections / safeTotal) * 100;
                const bar = document.createElement('div');
                bar.className = 'bar';
                bar.innerHTML = `
                    <div class="bar-label">${node.nodeId}</div>
                    <div class="bar-container">
                        <div class="bar-fill" style="width: ${percentage}%">
                            ${(node.connections || 0).toLocaleString()}
                        </div>
                    </div>
                    <div style="width: 60px; text-align: right;">${percentage.toFixed(1)}%</div>
                `;
                chart.appendChild(bar);
            });
        }
        
        function updateEventLog(events) {
            const log = document.getElementById('event-log');
            log.innerHTML = '';
            
            (events || []).reverse().forEach(event => {
                const eventElem = document.createElement('div');
                eventElem.className = 'event';
                const time = new Date(event.timestamp).toLocaleTimeString();
                eventElem.innerHTML = `
                    <span class="event-type">[${event.type}]</span> ${time}<br>
                    ${event.message}
                `;
                log.appendChild(eventElem);
            });
        }
        
        function clearEvents() {
            document.getElementById('event-log').innerHTML = 
                '<div class="event">Events cleared. Refresh to reload.</div>';
        }
        
        // Initial load
        refreshStats();
        refreshEvents();
        
        // Auto-refresh every 2 seconds
        refreshInterval = setInterval(() => {
            refreshStats();
            refreshEvents();
        }, 2000);
    </script>
</body>
</html>
        """;
    }
}
