package com.flux.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main Gateway Router application that demonstrates Consistent Hashing.
 * Exposes HTTP endpoints for testing and a real-time dashboard.
 */
public class GatewayRouter {
    
    private static final int VIRTUAL_NODES = 150;
    private final ConsistentHashRing<GatewayNode> ring;
    private final Map<String, GatewayNode> activeConnections;
    private final AtomicLong requestCounter;
    private final Map<GatewayNode, AtomicLong> nodeRequestCounts;
    
    public GatewayRouter() {
        this.ring = new ConsistentHashRing<>(VIRTUAL_NODES);
        this.activeConnections = new ConcurrentHashMap<>();
        this.requestCounter = new AtomicLong(0);
        this.nodeRequestCounts = new ConcurrentHashMap<>();
        
        // Initialize with 3 gateway nodes
        initializeNodes();
    }
    
    private void initializeNodes() {
        List<GatewayNode> initialNodes = List.of(
            new GatewayNode("gateway-node-01", "10.0.1.101", 9001),
            new GatewayNode("gateway-node-02", "10.0.1.102", 9002),
            new GatewayNode("gateway-node-03", "10.0.1.103", 9003)
        );
        
        for (GatewayNode node : initialNodes) {
            ring.addNode(node);
            nodeRequestCounts.put(node, new AtomicLong(0));
        }
        
        System.out.println("✓ Initialized ring with " + initialNodes.size() + " nodes");
        System.out.println("✓ Total virtual nodes: " + ring.size());
    }
    
    /**
     * Route a connection to the appropriate gateway node.
     */
    public GatewayNode routeConnection(String connectionId) {
        requestCounter.incrementAndGet();
        GatewayNode node = ring.get(connectionId);
        
        if (node != null) {
            activeConnections.put(connectionId, node);
            nodeRequestCounts.get(node).incrementAndGet();
        }
        
        return node;
    }
    
    /**
     * Add a new gateway node to the cluster.
     */
    public void addNode(GatewayNode node) {
        ring.addNode(node);
        nodeRequestCounts.put(node, new AtomicLong(0));
        System.out.println("✓ Added node: " + node.id());
        
        // Rebalance existing connections
        rebalanceConnections();
    }
    
    /**
     * Remove a gateway node from the cluster.
     */
    public void removeNode(GatewayNode node) {
        ring.removeNode(node);
        nodeRequestCounts.remove(node);
        System.out.println("✓ Removed node: " + node.id());
        
        // Rebalance connections that were on the removed node
        rebalanceConnections();
    }
    
    /**
     * Rebalance connections after topology change.
     */
    private void rebalanceConnections() {
        int relocated = 0;
        for (Map.Entry<String, GatewayNode> entry : activeConnections.entrySet()) {
            String connectionId = entry.getKey();
            GatewayNode currentNode = entry.getValue();
            GatewayNode newNode = ring.get(connectionId);
            
            if (newNode != null && !currentNode.equals(newNode)) {
                activeConnections.put(connectionId, newNode);
                AtomicLong oldCount = nodeRequestCounts.get(currentNode);
                if (oldCount != null) oldCount.decrementAndGet();
                nodeRequestCounts.get(newNode).incrementAndGet();
                relocated++;
            }
        }
        if (relocated > 0) {
            System.out.println("✓ Rebalanced: " + relocated + " connections relocated");
        }
    }
    
    /**
     * Get current distribution statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", requestCounter.get());
        stats.put("activeConnections", activeConnections.size());
        stats.put("virtualNodes", ring.size());
        stats.put("physicalNodes", ring.getNodes().size());
        
        Map<String, Long> distribution = new HashMap<>();
        for (Map.Entry<GatewayNode, AtomicLong> entry : nodeRequestCounts.entrySet()) {
            distribution.put(entry.getKey().id(), entry.getValue().get());
        }
        stats.put("nodeDistribution", distribution);
        
        // Calculate standard deviation
        long[] counts = nodeRequestCounts.values().stream()
            .mapToLong(AtomicLong::get)
            .toArray();
        double mean = Arrays.stream(counts).average().orElse(0.0);
        double variance = Arrays.stream(counts)
            .mapToDouble(c -> Math.pow(c - mean, 2))
            .average()
            .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        
        stats.put("mean", mean);
        stats.put("stdDev", stdDev);
        stats.put("uniformity", mean > 0 ? (stdDev / mean) : 0.0);
        
        return stats;
    }
    
    /**
     * Start the HTTP server for API and dashboard.
     */
    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        
        // API Endpoints
        server.createContext("/route", this::handleRoute);
        server.createContext("/stats", this::handleStats);
        server.createContext("/addNode", this::handleAddNode);
        server.createContext("/removeNode", this::handleRemoveNode);
        server.createContext("/simulate", this::handleSimulate);
        server.createContext("/dashboard", this::handleDashboard);
        
        server.start();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("🚀 Gateway Router started on port " + port);
        System.out.println("📊 Dashboard: http://localhost:" + port + "/dashboard");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    
    private void handleRoute(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String connectionId = body.trim();
        
        GatewayNode node = routeConnection(connectionId);
        String response = node != null ? node.getAddress() : "No nodes available";
        
        sendResponse(exchange, 200, response);
    }
    
    private void handleStats(HttpExchange exchange) throws IOException {
        Map<String, Object> stats = getStats();
        String json = toJson(stats);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        sendResponse(exchange, 200, json);
    }
    
    private void handleAddNode(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        int nodeNum = ring.getNodes().size() + 1;
        GatewayNode newNode = new GatewayNode(
            "gateway-node-" + String.format("%02d", nodeNum),
            "10.0.1." + (100 + nodeNum),
            9000 + nodeNum
        );
        
        addNode(newNode);
        sendResponse(exchange, 200, "Added node: " + newNode.id());
    }
    
    private void handleRemoveNode(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String nodeId = body.trim();
        
        GatewayNode toRemove = ring.getNodes().stream()
            .filter(n -> n.id().equals(nodeId))
            .findFirst()
            .orElse(null);
        
        if (toRemove != null) {
            removeNode(toRemove);
            sendResponse(exchange, 200, "Removed node: " + nodeId);
        } else {
            sendResponse(exchange, 404, "Node not found: " + nodeId);
        }
    }
    
    private void handleSimulate(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }
        
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int count = Integer.parseInt(body.trim());
        
        // Simulate connections
        for (int i = 0; i < count; i++) {
            String connectionId = "conn_" + UUID.randomUUID();
            routeConnection(connectionId);
        }
        
        sendResponse(exchange, 200, "Simulated " + count + " connections");
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = getDashboardHtml();
        exchange.getResponseHeaders().set("Content-Type", "text/html");
        sendResponse(exchange, 200, html);
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    @SuppressWarnings("unchecked")
    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;
            
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof Map) {
                json.append(toJson((Map<String, Object>) value));
            } else {
                json.append(value);
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    private String getDashboardHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Flux - Consistent Hashing Ring Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
            background: #0a0e27;
            color: #e0e0e0;
            padding: 20px;
        }
        .container { max-width: 1400px; margin: 0 auto; }
        h1 {
            color: #00ff9d;
            font-size: 2.5em;
            margin-bottom: 10px;
            text-shadow: 0 0 10px rgba(0, 255, 157, 0.5);
        }
        .subtitle { color: #888; margin-bottom: 30px; }
        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin-bottom: 30px; }
        .card {
            background: linear-gradient(135deg, #1a1f3a 0%, #0f1429 100%);
            border: 1px solid #2a3f5f;
            border-radius: 12px;
            padding: 20px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
        }
        .card h3 {
            color: #00ff9d;
            font-size: 0.9em;
            text-transform: uppercase;
            letter-spacing: 2px;
            margin-bottom: 15px;
        }
        .metric {
            font-size: 2.5em;
            font-weight: bold;
            color: #fff;
            margin-bottom: 5px;
        }
        .label { color: #888; font-size: 0.9em; }
        .ring-viz {
            position: relative;
            width: 400px;
            height: 400px;
            margin: 20px auto;
        }
        .ring-circle {
            position: absolute;
            width: 100%;
            height: 100%;
            border: 3px solid #2a3f5f;
            border-radius: 50%;
        }
        .virtual-node {
            position: absolute;
            width: 8px;
            height: 8px;
            border-radius: 50%;
            transform: translate(-50%, -50%);
            transition: all 0.3s ease;
        }
        .node-01 { background: #ff6b6b; box-shadow: 0 0 10px #ff6b6b; }
        .node-02 { background: #4ecdc4; box-shadow: 0 0 10px #4ecdc4; }
        .node-03 { background: #ffe66d; box-shadow: 0 0 10px #ffe66d; }
        .controls { margin-top: 30px; display: flex; gap: 15px; flex-wrap: wrap; }
        button {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 8px;
            cursor: pointer;
            font-family: inherit;
            font-size: 0.9em;
            transition: all 0.3s ease;
            box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
        }
        button:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
        }
        button:active { transform: translateY(0); }
        .bar-chart {
            display: flex;
            align-items: flex-end;
            height: 200px;
            gap: 10px;
            margin-top: 20px;
        }
        .bar {
            flex: 1;
            background: linear-gradient(to top, #667eea, #764ba2);
            border-radius: 4px 4px 0 0;
            position: relative;
            min-height: 20px;
            transition: height 0.5s ease;
        }
        .bar-label {
            position: absolute;
            bottom: -25px;
            left: 50%;
            transform: translateX(-50%);
            font-size: 0.8em;
            color: #888;
            white-space: nowrap;
        }
        .bar-value {
            position: absolute;
            top: -25px;
            left: 50%;
            transform: translateX(-50%);
            font-size: 0.9em;
            color: #00ff9d;
            font-weight: bold;
        }
        input[type="number"] {
            background: #1a1f3a;
            border: 1px solid #2a3f5f;
            color: #e0e0e0;
            padding: 8px 12px;
            border-radius: 6px;
            font-family: inherit;
            width: 120px;
        }
        .status { 
            display: inline-block; 
            padding: 4px 8px; 
            border-radius: 4px; 
            font-size: 0.8em; 
            margin-left: 10px;
        }
        .status.good { background: #2d5f3f; color: #00ff9d; }
        .status.warning { background: #5f4a2d; color: #ffb84d; }
    </style>
</head>
<body>
    <div class="container">
        <h1>⚡ Flux Consistent Hashing Ring</h1>
        <p class="subtitle">Day 49: Binary Search Lookup Performance Monitor</p>
        
        <div class="grid">
            <div class="card">
                <h3>Total Requests</h3>
                <div class="metric" id="totalRequests">0</div>
                <div class="label">Routing decisions made</div>
            </div>
            
            <div class="card">
                <h3>Active Connections</h3>
                <div class="metric" id="activeConnections">0</div>
                <div class="label">Currently tracked</div>
            </div>
            
            <div class="card">
                <h3>Ring Size</h3>
                <div class="metric" id="virtualNodes">0</div>
                <div class="label">Virtual nodes</div>
            </div>
            
            <div class="card">
                <h3>Distribution Uniformity</h3>
                <div class="metric" id="uniformity">0.00%</div>
                <div class="label" id="uniformityStatus">σ/μ ratio</div>
            </div>
        </div>
        
        <div class="card">
            <h3>Ring Visualization (150 replicas × 3 nodes)</h3>
            <div class="ring-viz" id="ringViz">
                <div class="ring-circle"></div>
            </div>
        </div>
        
        <div class="card">
            <h3>Node Distribution</h3>
            <div class="bar-chart" id="barChart"></div>
        </div>
        
        <div class="controls">
            <button onclick="addNode()">➕ Add Gateway Node</button>
            <button onclick="removeRandomNode()">➖ Remove Random Node</button>
            <input type="number" id="simCount" value="10000" min="100" max="1000000">
            <button onclick="simulate()">🔄 Simulate Connections</button>
            <button onclick="clearConnections()">🗑️ Clear All</button>
        </div>
    </div>
    
    <script>
        async function fetchStats() {
            try {
                const response = await fetch('/stats');
                const stats = await response.json();
                updateDashboard(stats);
            } catch (e) {
                console.error('Failed to fetch stats:', e);
            }
        }
        
        function updateDashboard(stats) {
            document.getElementById('totalRequests').textContent = (stats.totalRequests || 0).toLocaleString();
            document.getElementById('activeConnections').textContent = (stats.activeConnections || 0).toLocaleString();
            document.getElementById('virtualNodes').textContent = stats.virtualNodes || 0;
            
            const uniformity = ((stats.uniformity || 0) * 100).toFixed(2);
            document.getElementById('uniformity').textContent = uniformity + '%';
            
            const statusEl = document.getElementById('uniformityStatus');
            const u = stats.uniformity || 0;
            if (u < 0.05) {
                statusEl.innerHTML = 'σ/μ ratio <span class="status good">Excellent</span>';
            } else if (u < 0.10) {
                statusEl.innerHTML = 'σ/μ ratio <span class="status warning">Fair</span>';
            } else {
                statusEl.innerHTML = 'σ/μ ratio <span class="status">Poor</span>';
            }
            
            updateBarChart(stats.nodeDistribution);
        }
        
        function updateBarChart(distribution) {
            const chartEl = document.getElementById('barChart');
            chartEl.innerHTML = '';
            
            if (!distribution || Object.keys(distribution).length === 0) return;
            
            const maxCount = Math.max(...Object.values(distribution));
            
            for (const [nodeId, count] of Object.entries(distribution)) {
                const bar = document.createElement('div');
                bar.className = 'bar';
                const heightPercent = maxCount > 0 ? (count / maxCount) * 100 : 0;
                bar.style.height = heightPercent + '%';
                
                const label = document.createElement('div');
                label.className = 'bar-label';
                label.textContent = nodeId;
                
                const value = document.createElement('div');
                value.className = 'bar-value';
                value.textContent = Number(count).toLocaleString();
                
                bar.appendChild(label);
                bar.appendChild(value);
                chartEl.appendChild(bar);
            }
        }
        
        async function addNode() {
            await fetch('/addNode', { method: 'POST' });
            await fetchStats();
        }
        
        async function removeRandomNode() {
            await fetch('/removeNode', {
                method: 'POST',
                headers: { 'Content-Type': 'text/plain' },
                body: 'gateway-node-02'
            });
            await fetchStats();
        }
        
        async function simulate() {
            const count = document.getElementById('simCount').value;
            await fetch('/simulate', {
                method: 'POST',
                headers: { 'Content-Type': 'text/plain' },
                body: count
            });
            await fetchStats();
        }
        
        function clearConnections() {
            location.reload();
        }
        
        // Auto-refresh every 2 seconds
        setInterval(fetchStats, 2000);
        fetchStats();
    </script>
</body>
</html>
""";
    }
    
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        GatewayRouter router = new GatewayRouter();
        router.start(port);
    }
}
