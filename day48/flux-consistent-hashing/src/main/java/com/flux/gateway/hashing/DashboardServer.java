package com.flux.gateway.hashing;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lightweight HTTP server for the interactive dashboard.
 * Uses only JDK built-in com.sun.net.httpserver - no external dependencies.
 */
public final class DashboardServer {
    
    private final HttpServer server;
    private final ConsistentHashRing ring;
    private final List<String> simulatedKeys;
    
    public DashboardServer(ConsistentHashRing ring, int port) throws IOException {
        this.ring = ring;
        this.simulatedKeys = new ArrayList<>();
        
        // Generate 1000 simulated session keys
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            simulatedKeys.add("session:" + random.nextLong());
        }
        
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            
            String response;
            String contentType;
            
            if (path.equals("/") || path.equals("/dashboard")) {
                response = getDashboardHtml();
                contentType = "text/html; charset=UTF-8";
            } else if (path.equals("/api/state")) {
                response = getRingStateJson();
                contentType = "application/json";
            } else if (path.startsWith("/api/add-node")) {
                int nodeNum = Integer.parseInt(exchange.getRequestURI().getQuery().split("=")[1]);
                addNode(nodeNum);
                response = getRingStateJson();
                contentType = "application/json";
            } else if (path.startsWith("/api/remove-node")) {
                String nodeId = exchange.getRequestURI().getQuery().split("=")[1];
                ring.removeNode(nodeId);
                response = getRingStateJson();
                contentType = "application/json";
            } else {
                response = "Not Found";
                contentType = "text/plain";
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }
            
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        });
    }
    
    public void start() {
        server.start();
        System.out.println("Dashboard running at http://localhost:" + server.getAddress().getPort() + "/dashboard");
    }
    
    public void stop() {
        server.stop(0);
    }
    
    private void addNode(int nodeNum) {
        String nodeId = "node-" + nodeNum;
        String address = "10.0." + (nodeNum / 256) + "." + (nodeNum % 256);
        ring.addNode(new PhysicalNode(nodeId, address));
    }
    
    private String getRingStateJson() {
        var snapshot = ring.getSnapshot();
        Map<String, Integer> distribution = new HashMap<>();
        
        // Calculate current distribution
        for (String key : simulatedKeys) {
            PhysicalNode node = ring.getNode(key);
            distribution.merge(node.nodeId(), 1, Integer::sum);
        }
        
        double stdDev = DistributionAnalyzer.calculateStandardDeviation(distribution);
        double gini = DistributionAnalyzer.calculateGiniCoefficient(distribution);
        
        // Build JSON manually (no Jackson dependency)
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"physicalNodes\":").append(ring.getPhysicalNodeCount()).append(",");
        json.append("\"virtualNodes\":").append(ring.getVirtualNodeCount()).append(",");
        json.append("\"totalKeys\":").append(simulatedKeys.size()).append(",");
        json.append("\"stdDevPct\":").append(String.format("%.2f", stdDev)).append(",");
        json.append("\"gini\":").append(String.format("%.4f", gini)).append(",");
        
        json.append("\"distribution\":[");
        boolean first = true;
        for (var entry : distribution.entrySet()) {
            if (!first) json.append(",");
            json.append("{\"nodeId\":\"").append(entry.getKey()).append("\",");
            json.append("\"keyCount\":").append(entry.getValue()).append("}");
            first = false;
        }
        json.append("],");
        
        json.append("\"virtualNodePositions\":[");
        first = true;
        for (var entry : snapshot.virtualNodes().entrySet()) {
            if (!first) json.append(",");
            long hash = entry.getKey();
            // Normalize to 0-360 degrees for circular visualization (use double to avoid long overflow)
            double range = (double) Long.MAX_VALUE - (double) Long.MIN_VALUE;
            double angle = ((double) hash - (double) Long.MIN_VALUE) / range * 360.0;
            json.append("{\"hash\":").append(hash).append(",");
            json.append("\"angle\":").append(String.format("%.2f", angle)).append(",");
            json.append("\"nodeId\":\"").append(entry.getValue().nodeId()).append("\"}");
            first = false;
        }
        json.append("]");
        
        json.append("}");
        return json.toString();
    }
    
    private String getDashboardHtml() {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Flux Consistent Hashing - Live Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            font-family: 'Monaco', 'Courier New', monospace; 
            background: #0a0e27; 
            color: #e0e0e0; 
            padding: 20px;
        }
        .container { max-width: 1400px; margin: 0 auto; }
        h1 { 
            font-size: 28px; 
            margin-bottom: 10px; 
            color: #00d4ff;
            text-shadow: 0 0 10px rgba(0, 212, 255, 0.5);
        }
        .subtitle { 
            font-size: 14px; 
            color: #888; 
            margin-bottom: 30px; 
        }
        .metrics {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin-bottom: 30px;
        }
        .metric {
            background: #151b3d;
            padding: 15px;
            border-radius: 8px;
            border: 1px solid #1e2847;
        }
        .metric-label {
            font-size: 12px;
            color: #888;
            text-transform: uppercase;
            margin-bottom: 5px;
        }
        .metric-value {
            font-size: 24px;
            font-weight: bold;
            color: #00d4ff;
        }
        .metric-unit {
            font-size: 14px;
            color: #888;
            margin-left: 5px;
        }
        .controls {
            background: #151b3d;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 30px;
            border: 1px solid #1e2847;
        }
        .controls h2 {
            font-size: 16px;
            margin-bottom: 15px;
            color: #00d4ff;
        }
        button {
            background: #1e2847;
            color: #00d4ff;
            border: 1px solid #00d4ff;
            padding: 10px 20px;
            margin-right: 10px;
            margin-bottom: 10px;
            border-radius: 5px;
            cursor: pointer;
            font-family: inherit;
            font-size: 14px;
            transition: all 0.2s;
        }
        button:hover {
            background: #00d4ff;
            color: #0a0e27;
        }
        .visualization {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 30px;
        }
        .panel {
            background: #151b3d;
            padding: 20px;
            border-radius: 8px;
            border: 1px solid #1e2847;
        }
        .panel h3 {
            font-size: 14px;
            color: #888;
            margin-bottom: 15px;
            text-transform: uppercase;
        }
        #ring-canvas {
            width: 100%;
            height: 400px;
            background: #0a0e27;
            border-radius: 5px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
        }
        th {
            background: #1e2847;
            padding: 10px;
            text-align: left;
            font-size: 12px;
            color: #888;
            text-transform: uppercase;
        }
        td {
            padding: 10px;
            border-top: 1px solid #1e2847;
            font-size: 14px;
        }
        .bar {
            background: #00d4ff;
            height: 20px;
            border-radius: 3px;
            transition: width 0.3s;
        }
        .status {
            display: inline-block;
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: #00ff00;
            margin-right: 5px;
            animation: pulse 2s infinite;
        }
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.3; }
        }
    </style>
</head>
<body>
    <div class="container">
        <h1><span class="status"></span>FLUX Consistent Hashing</h1>
        <div class="subtitle">Real-time visualization of the hash ring • Virtual Threads: Active</div>
        
        <div class="metrics">
            <div class="metric">
                <div class="metric-label">Physical Nodes</div>
                <div class="metric-value" id="physical-nodes">0</div>
            </div>
            <div class="metric">
                <div class="metric-label">Virtual Nodes</div>
                <div class="metric-value" id="virtual-nodes">0</div>
            </div>
            <div class="metric">
                <div class="metric-label">Total Keys</div>
                <div class="metric-value" id="total-keys">0</div>
            </div>
            <div class="metric">
                <div class="metric-label">Std Deviation</div>
                <div class="metric-value" id="std-dev">0<span class="metric-unit">%</span></div>
            </div>
            <div class="metric">
                <div class="metric-label">Gini Coefficient</div>
                <div class="metric-value" id="gini">0.0000</div>
            </div>
        </div>
        
        <div class="controls">
            <h2>Cluster Operations</h2>
            <button onclick="addNode()">Add Node</button>
            <button onclick="removeRandomNode()">Remove Random Node</button>
            <button onclick="addMultipleNodes(10)">Add 10 Nodes</button>
            <button onclick="refresh()">Refresh</button>
        </div>
        
        <div class="visualization">
            <div class="panel">
                <h3>Hash Ring Visualization</h3>
                <canvas id="ring-canvas"></canvas>
            </div>
            
            <div class="panel">
                <h3>Key Distribution by Node</h3>
                <div style="max-height: 400px; overflow-y: auto;">
                    <table id="distribution-table">
                        <thead>
                            <tr>
                                <th>Node ID</th>
                                <th>Key Count</th>
                                <th>Distribution</th>
                            </tr>
                        </thead>
                        <tbody></tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
    
    <script>
        let nodeCounter = 10;
        let state = null;
        
        async function fetchState() {
            const response = await fetch('/api/state');
            state = await response.json();
            updateUI();
        }
        
        async function addNode() {
            nodeCounter++;
            await fetch('/api/add-node?id=' + nodeCounter);
            await fetchState();
        }
        
        async function removeRandomNode() {
            if (state.distribution.length === 0) return;
            const randomNode = state.distribution[Math.floor(Math.random() * state.distribution.length)];
            await fetch('/api/remove-node?id=' + randomNode.nodeId);
            await fetchState();
        }
        
        async function addMultipleNodes(count) {
            for (let i = 0; i < count; i++) {
                nodeCounter++;
                await fetch('/api/add-node?id=' + nodeCounter);
            }
            await fetchState();
        }
        
        function refresh() {
            fetchState();
        }
        
        function updateUI() {
            document.getElementById('physical-nodes').textContent = state.physicalNodes;
            document.getElementById('virtual-nodes').textContent = state.virtualNodes;
            document.getElementById('total-keys').textContent = state.totalKeys;
            document.getElementById('std-dev').innerHTML = state.stdDevPct + '<span class="metric-unit">%</span>';
            document.getElementById('gini').textContent = state.gini;
            
            drawRing();
            updateDistributionTable();
        }
        
        function drawRing() {
            const canvas = document.getElementById('ring-canvas');
            const ctx = canvas.getContext('2d');
            const rect = canvas.getBoundingClientRect();
            canvas.width = rect.width * window.devicePixelRatio;
            canvas.height = rect.height * window.devicePixelRatio;
            ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
            
            const centerX = rect.width / 2;
            const centerY = rect.height / 2;
            const radius = Math.min(centerX, centerY) - 40;
            
            ctx.clearRect(0, 0, rect.width, rect.height);
            
            // Draw ring circle
            ctx.strokeStyle = '#1e2847';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
            ctx.stroke();
            
            // Generate colors for nodes
            const nodeColors = {};
            const nodes = [...new Set(state.virtualNodePositions.map(vn => vn.nodeId))];
            nodes.forEach((nodeId, i) => {
                const hue = (i * 360 / nodes.length) % 360;
                nodeColors[nodeId] = `hsl(${hue}, 70%, 50%)`;
            });
            
            // Draw virtual nodes
            state.virtualNodePositions.forEach(vnode => {
                const angle = (vnode.angle - 90) * Math.PI / 180;
                const x = centerX + radius * Math.cos(angle);
                const y = centerY + radius * Math.sin(angle);
                
                ctx.fillStyle = nodeColors[vnode.nodeId];
                ctx.beginPath();
                ctx.arc(x, y, 3, 0, Math.PI * 2);
                ctx.fill();
            });
            
            // Draw legend
            let legendY = 20;
            nodes.forEach(nodeId => {
                ctx.fillStyle = nodeColors[nodeId];
                ctx.fillRect(10, legendY, 12, 12);
                ctx.fillStyle = '#e0e0e0';
                ctx.font = '11px Monaco';
                ctx.fillText(nodeId, 28, legendY + 10);
                legendY += 18;
            });
        }
        
        function updateDistributionTable() {
            const tbody = document.querySelector('#distribution-table tbody');
            tbody.innerHTML = '';
            
            const maxKeys = Math.max(...state.distribution.map(d => d.keyCount));
            
            state.distribution
                .sort((a, b) => b.keyCount - a.keyCount)
                .forEach(node => {
                    const tr = document.createElement('tr');
                    const barWidth = (node.keyCount / maxKeys * 100) + '%';
                    
                    tr.innerHTML = `
                        <td>${node.nodeId}</td>
                        <td>${node.keyCount}</td>
                        <td><div class="bar" style="width: ${barWidth}"></div></td>
                    `;
                    tbody.appendChild(tr);
                });
        }
        
        // Initial load and auto-refresh
        fetchState();
        setInterval(fetchState, 5000);
    </script>
</body>
</html>
        """;
    }
}
