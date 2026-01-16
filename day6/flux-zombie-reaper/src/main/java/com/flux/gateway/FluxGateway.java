package com.flux.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class FluxGateway {
    private final TimeoutWheel wheel;
    private final ConnectionRegistry registry;
    private final ZombieReaper reaper;
    private final MetricsCollector metrics;
    private final HttpServer dashboardServer;
    private final List<Connection> demoConnections;
    private final AtomicInteger zombieStartIndex;
    
    public FluxGateway(int dashboardPort) throws IOException {
        this.wheel = new TimeoutWheel();
        this.registry = new ConnectionRegistry();
        this.reaper = new ZombieReaper(wheel, registry);
        this.metrics = new MetricsCollector();
        this.dashboardServer = createDashboardServer(dashboardPort);
        this.demoConnections = new ArrayList<>();
        this.zombieStartIndex = new AtomicInteger(0);
    }
    
    public void start() {
        reaper.start();
        dashboardServer.start();
        System.out.println("✅ Flux Gateway started");
        System.out.println("📊 Dashboard: http://localhost:" + dashboardServer.getAddress().getPort() + "/dashboard");
    }
    
    public void stop() {
        reaper.stop();
        dashboardServer.stop(0);
        registry.clear();
        System.out.println("🛑 Flux Gateway stopped");
    }
    
    public TimeoutWheel getWheel() {
        return wheel;
    }
    
    public ConnectionRegistry getRegistry() {
        return registry;
    }
    
    public ZombieReaper getReaper() {
        return reaper;
    }
    
    public MetricsCollector getMetrics() {
        return metrics;
    }
    
    private HttpServer createDashboardServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Dashboard HTML endpoint
        server.createContext("/dashboard", exchange -> {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String html = generateDashboardHtml();
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        // Handle favicon and other common requests
        server.createContext("/favicon.ico", exchange -> {
            exchange.sendResponseHeaders(204, -1); // No Content
        });
        
        // Catch-all for undefined paths to prevent 404 errors
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) {
                // Redirect root to dashboard
                exchange.getResponseHeaders().set("Location", "/dashboard");
                exchange.sendResponseHeaders(302, -1);
            } else if (!path.startsWith("/dashboard") && !path.startsWith("/metrics") && !path.startsWith("/demo/")) {
                // Return 404 for unknown paths
                String response = "{\"error\":\"Not Found\",\"path\":\"" + path + "\"}";
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(404, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        });
        
        // Metrics JSON endpoint
        server.createContext("/metrics", exchange -> {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String json = generateMetricsJson();
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        // Demo endpoints
        server.createContext("/demo/spawn", exchange -> {
            // Handle CORS preflight
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            String query = exchange.getRequestURI().getQuery();
            int count = 100;
            if (query != null && query.contains("count=")) {
                try {
                    count = Integer.parseInt(query.split("count=")[1].split("&")[0]);
                } catch (Exception e) {
                    // Use default
                }
            }
            
            spawnConnections(count);
            
            String json = "{\"success\":true,\"spawned\":" + count + "}";
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.createContext("/demo/partition", exchange -> {
            // Handle CORS preflight
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            simulatePartition(500);
            
            String json = "{\"success\":true,\"zombies\":500}";
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.createContext("/demo/run", exchange -> {
            // Handle CORS preflight
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            // Run full demo scenario in background
            Thread.ofVirtual().start(() -> runDemoScenario());
            
            String json = "{\"success\":true,\"message\":\"Demo scenario started\"}";
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        // Reset endpoint to clear all connections and start fresh
        server.createContext("/demo/reset", exchange -> {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            // Clear all connections
            demoConnections.clear();
            registry.clear();
            zombieStartIndex.set(0);
            
            System.out.println("🔄 Reset: All connections cleared");
            
            String json = "{\"success\":true,\"message\":\"Reset complete - all connections cleared\"}";
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        // Status page endpoint
        server.createContext("/status", exchange -> {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String html = generateStatusPage();
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.setExecutor(null); // Use default executor
        return server;
    }
    
    private String generateMetricsJson() {
        var wheelStats = wheel.getStats();
        var metricsSnapshot = metrics.snapshot();
        
        StringBuilder json = new StringBuilder("{");
        json.append("\"activeConnections\":").append(registry.getActiveCount()).append(",");
        json.append("\"zombiesKilled\":").append(reaper.getZombiesKilled()).append(",");
        json.append("\"currentSlot\":").append(wheelStats.currentSlot()).append(",");
        json.append("\"wheelActiveConnections\":").append(wheelStats.activeConnections()).append(",");
        json.append("\"totalScheduled\":").append(wheelStats.totalScheduled()).append(",");
        json.append("\"totalExpired\":").append(wheelStats.totalExpired()).append(",");
        json.append("\"heartbeatsReceived\":").append(metricsSnapshot.heartbeatsReceived()).append(",");
        json.append("\"heartbeatsSent\":").append(metricsSnapshot.heartbeatsSent()).append(",");
        json.append("\"bucketDistribution\":[");
        
        int[] dist = wheelStats.bucketDistribution();
        for (int i = 0; i < dist.length; i++) {
            json.append(dist[i]);
            if (i < dist.length - 1) json.append(",");
        }
        json.append("]}");
        
        return json.toString();
    }
    
    private void spawnConnections(int count) {
        try {
            for (int i = 0; i < count; i++) {
                Connection conn = new Connection(SocketChannel.open());
                registry.register(conn);
                demoConnections.add(conn);
                wheel.schedule(conn.id(), 30);
                metrics.recordConnectionAccepted();
            }
            System.out.println("✅ Spawned " + count + " connections");
        } catch (IOException e) {
            System.err.println("Error spawning connections: " + e.getMessage());
        }
    }
    
    private void simulatePartition(int zombieCount) {
        int startIndex = zombieStartIndex.get();
        int endIndex = Math.min(startIndex + zombieCount, demoConnections.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            Connection conn = demoConnections.get(i);
            // Stop sending heartbeats - don't reschedule in wheel
            // These will become zombies
        }
        
        zombieStartIndex.set(endIndex);
        System.out.println("🔌 Simulated partition - " + zombieCount + " connections will stop heartbeating");
    }
    
    private void runDemoScenario() {
        System.out.println("🎬 Running Demo Scenario...");
        
        // Spawn 1000 connections
        spawnConnections(1000);
        
        // Wait a bit
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // Simulate partition - 200 connections become zombies
        simulatePartition(200);
        
        // Start heartbeat simulation for non-zombie connections
        Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    
                    // Send heartbeats for non-zombie connections
                    int zombieStart = zombieStartIndex.get();
                    for (int i = zombieStart; i < demoConnections.size(); i++) {
                        Connection conn = demoConnections.get(i);
                        if (conn.isOpen() && registry.get(conn.id()).isPresent()) {
                            conn.updateLastHeartbeat();
                            wheel.schedule(conn.id(), 30);
                            metrics.recordHeartbeatReceived();
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        
        System.out.println("✅ Demo scenario running - zombies will be reaped in ~35 seconds");
    }
    
    private String generateDashboardHtml() {
        return """
<!DOCTYPE html>
<html>
<head>
    <title>Flux Gateway - Zombie Reaper Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #fff;
            padding: 20px;
            min-height: 100vh;
        }
        .container { max-width: 1400px; margin: 0 auto; }
        h1 {
            font-size: 2.5rem;
            margin-bottom: 10px;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }
        .subtitle {
            font-size: 1.1rem;
            opacity: 0.9;
            margin-bottom: 30px;
        }
        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .card {
            background: rgba(255, 255, 255, 0.1);
            backdrop-filter: blur(10px);
            border-radius: 15px;
            padding: 25px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
        }
        .metric-value {
            font-size: 3rem;
            font-weight: bold;
            margin: 10px 0;
        }
        .metric-label {
            font-size: 0.9rem;
            opacity: 0.8;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        #wheelCanvas {
            width: 100%;
            height: 400px;
            background: rgba(0, 0, 0, 0.2);
            border-radius: 10px;
        }
        .controls {
            display: flex;
            gap: 15px;
            flex-wrap: wrap;
            margin-top: 20px;
        }
        button {
            background: rgba(255, 255, 255, 0.2);
            border: 2px solid rgba(255, 255, 255, 0.3);
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            font-size: 1rem;
            cursor: pointer;
            transition: all 0.3s ease;
        }
        button:hover {
            background: rgba(255, 255, 255, 0.3);
            transform: translateY(-2px);
        }
        .status-badge {
            display: inline-block;
            padding: 6px 12px;
            border-radius: 20px;
            font-size: 0.85rem;
            font-weight: 600;
            margin-top: 10px;
        }
        .status-running { background: #10b981; }
        .status-stopped { background: #ef4444; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🔪 Zombie Reaper Dashboard</h1>
        <div class="subtitle">Real-time Timeout Wheel Visualization</div>
        
        <div class="grid">
            <div class="card">
                <div class="metric-label">Active Connections</div>
                <div class="metric-value" id="activeConnections">0</div>
                <div class="status-badge status-running">MONITORING</div>
            </div>
            <div class="card">
                <div class="metric-label">Zombies Killed</div>
                <div class="metric-value" id="zombiesKilled">0</div>
            </div>
            <div class="card">
                <div class="metric-label">Current Slot</div>
                <div class="metric-value" id="currentSlot">0</div>
                <div class="metric-label">/ 60 slots</div>
            </div>
            <div class="card">
                <div class="metric-label">Reaper Latency</div>
                <div class="metric-value" id="reaperLatency">&lt;1ms</div>
            </div>
        </div>
        
        <div class="card">
            <h2 style="margin-bottom: 15px;">⏰ Timeout Wheel (60 seconds)</h2>
            <canvas id="wheelCanvas"></canvas>
            
            <div class="controls">
                <button onclick="spawnConnections(100)">Spawn 100 Connections</button>
                <button onclick="spawnConnections(1000)">Spawn 1000 Connections</button>
                <button onclick="simulatePartition()">Simulate Partition (500 zombies)</button>
                <button onclick="runDemo()" style="background: rgba(239, 68, 68, 0.3);">Run Full Demo</button>
                <button onclick="resetAll()" style="background: rgba(255, 193, 7, 0.3);">🔄 Reset All</button>
            </div>
            <div style="margin-top: 15px; font-size: 0.9rem; opacity: 0.8;">
                <span id="lastUpdate">Last update: --</span> | 
                <span id="refreshStatus">🟢 Auto-refreshing every 1s</span>
            </div>
        </div>
    </div>
    
    <script>
        const canvas = document.getElementById('wheelCanvas');
        const ctx = canvas.getContext('2d');
        let metrics = {};
        
        function resizeCanvas() {
            const rect = canvas.getBoundingClientRect();
            canvas.width = rect.width;
            canvas.height = rect.height;
        }
        
        resizeCanvas();
        window.addEventListener('resize', resizeCanvas);
        
        function drawWheel() {
            const centerX = canvas.width / 2;
            const centerY = canvas.height / 2;
            const radius = Math.min(centerX, centerY) - 40;
            const slotAngle = (2 * Math.PI) / 60;
            
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            
            // Draw slots
            for (let i = 0; i < 60; i++) {
                const angle = i * slotAngle - Math.PI / 2;
                const nextAngle = (i + 1) * slotAngle - Math.PI / 2;
                
                const x1 = centerX + radius * Math.cos(angle);
                const y1 = centerY + radius * Math.sin(angle);
                const x2 = centerX + radius * Math.cos(nextAngle);
                const y2 = centerY + radius * Math.sin(nextAngle);
                
                // Highlight current slot
                const isCurrent = i === metrics.currentSlot;
                const connCount = metrics.bucketDistribution ? metrics.bucketDistribution[i] : 0;
                
                ctx.beginPath();
                ctx.moveTo(centerX, centerY);
                ctx.lineTo(x1, y1);
                ctx.arc(centerX, centerY, radius, angle, nextAngle);
                ctx.closePath();
                
                if (isCurrent) {
                    ctx.fillStyle = 'rgba(239, 68, 68, 0.8)';
                } else if (connCount > 0) {
                    const intensity = Math.min(connCount / 50, 1);
                    ctx.fillStyle = `rgba(59, 130, 246, ${0.3 + intensity * 0.5})`;
                } else {
                    ctx.fillStyle = 'rgba(255, 255, 255, 0.1)';
                }
                ctx.fill();
                
                ctx.strokeStyle = 'rgba(255, 255, 255, 0.2)';
                ctx.lineWidth = 1;
                ctx.stroke();
                
                // Draw connection count
                if (connCount > 0) {
                    const midAngle = angle + slotAngle / 2;
                    const textRadius = radius * 0.7;
                    const textX = centerX + textRadius * Math.cos(midAngle);
                    const textY = centerY + textRadius * Math.sin(midAngle);
                    
                    ctx.fillStyle = 'white';
                    ctx.font = 'bold 12px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'middle';
                    ctx.fillText(connCount, textX, textY);
                }
            }
            
            // Draw center circle
            ctx.beginPath();
            ctx.arc(centerX, centerY, 30, 0, 2 * Math.PI);
            ctx.fillStyle = 'rgba(255, 255, 255, 0.2)';
            ctx.fill();
            
            // Draw current slot indicator
            const currentAngle = metrics.currentSlot * slotAngle - Math.PI / 2;
            const indicatorX = centerX + (radius + 20) * Math.cos(currentAngle);
            const indicatorY = centerY + (radius + 20) * Math.sin(currentAngle);
            
            ctx.beginPath();
            ctx.arc(indicatorX, indicatorY, 8, 0, 2 * Math.PI);
            ctx.fillStyle = '#ef4444';
            ctx.fill();
        }
        
        let lastUpdateTime = null;
        let refreshInterval = null;
        
        function updateMetrics() {
            fetch('/metrics')
                .then(res => {
                    if (!res.ok) {
                        throw new Error('Failed to fetch metrics: ' + res.status);
                    }
                    return res.json();
                })
                .then(data => {
                    metrics = data;
                    document.getElementById('activeConnections').textContent = data.activeConnections;
                    document.getElementById('zombiesKilled').textContent = data.zombiesKilled;
                    document.getElementById('currentSlot').textContent = data.currentSlot;
                    drawWheel();
                    
                    // Update last update time
                    lastUpdateTime = new Date();
                    const timeStr = lastUpdateTime.toLocaleTimeString();
                    document.getElementById('lastUpdate').textContent = 'Last update: ' + timeStr;
                    document.getElementById('refreshStatus').textContent = '🟢 Auto-refreshing every 1s';
                })
                .catch(err => {
                    console.error('Error updating metrics:', err);
                    document.getElementById('refreshStatus').textContent = '🔴 Refresh error';
                });
        }
        
        function resetAll() {
            if (!confirm('Reset all connections and metrics? This will clear everything.')) {
                return;
            }
            
            fetch('/demo/reset', { 
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            })
                .then(res => {
                    if (!res.ok) {
                        throw new Error('Failed to reset: ' + res.status + ' ' + res.statusText);
                    }
                    return res.json();
                })
                .then(data => {
                    console.log('Reset complete:', data.message);
                    // Refresh metrics immediately
                    updateMetrics();
                })
                .catch(err => {
                    console.error('Error resetting:', err);
                    alert('Failed to reset: ' + err.message);
                });
        }
        
        function spawnConnections(count) {
            fetch('/demo/spawn?count=' + count, { 
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            })
                .then(res => {
                    if (!res.ok) {
                        throw new Error('Failed to spawn connections: ' + res.status + ' ' + res.statusText);
                    }
                    return res.json();
                })
                .then(data => {
                    console.log('Spawned ' + data.spawned + ' connections');
                    updateMetrics();
                })
                .catch(err => {
                    console.error('Error spawning connections:', err);
                    alert('Failed to spawn connections: ' + err.message);
                });
        }
        
        function simulatePartition() {
            fetch('/demo/partition', { 
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            })
                .then(res => {
                    if (!res.ok) {
                        throw new Error('Failed to simulate partition: ' + res.status + ' ' + res.statusText);
                    }
                    return res.json();
                })
                .then(data => {
                    console.log('Simulated partition - ' + data.zombies + ' connections will become zombies');
                    updateMetrics();
                })
                .catch(err => {
                    console.error('Error simulating partition:', err);
                    alert('Failed to simulate partition: ' + err.message);
                });
        }
        
        function runDemo() {
            fetch('/demo/run', { 
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            })
                .then(res => {
                    if (!res.ok) {
                        throw new Error('Failed to run demo: ' + res.status + ' ' + res.statusText);
                    }
                    return res.json();
                })
                .then(data => {
                    console.log('Demo scenario started');
                    updateMetrics();
                })
                .catch(err => {
                    console.error('Error running demo:', err);
                    alert('Failed to run demo: ' + err.message);
                });
        }
        
        function advanceWheel(seconds) {
            // Not implemented - wheel advances automatically
            alert('Wheel advances automatically every second. This is for visualization only.');
        }
        
        // Update every second - keep refreshing continuously
        updateMetrics();
        refreshInterval = setInterval(updateMetrics, 1000);
        
        // Ensure refresh continues even if there are errors
        window.addEventListener('error', function(e) {
            console.error('Global error:', e);
            // Keep refreshing
            if (!refreshInterval) {
                refreshInterval = setInterval(updateMetrics, 1000);
            }
        });
    </script>
</body>
</html>
        """;
    }
    
    private String generateStatusPage() {
        var wheelStats = wheel.getStats();
        var metricsSnapshot = metrics.snapshot();
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <title>Flux Gateway - Status</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #fff; padding: 20px; min-height: 100vh; }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; }\n");
        html.append("        h1 { font-size: 2.5rem; margin-bottom: 10px; text-shadow: 2px 2px 4px rgba(0,0,0,0.3); }\n");
        html.append("        .card { background: rgba(255, 255, 255, 0.1); backdrop-filter: blur(10px); border-radius: 15px; padding: 25px; margin: 20px 0; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3); }\n");
        html.append("        .metric-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid rgba(255, 255, 255, 0.1); }\n");
        html.append("        .metric-label { font-weight: 600; opacity: 0.9; }\n");
        html.append("        .metric-value { font-size: 1.2rem; font-weight: bold; }\n");
        html.append("        .status-running { color: #10b981; }\n");
        html.append("        .status-stopped { color: #ef4444; }\n");
        html.append("        .link { color: #fff; text-decoration: underline; margin: 20px 0; display: inline-block; }\n");
        html.append("        .link:hover { opacity: 0.8; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <h1>📊 Flux Gateway Status</h1>\n");
        html.append("        <a href=\"/dashboard\" class=\"link\">← Back to Dashboard</a>\n");
        html.append("        <div class=\"card\">\n");
        html.append("            <h2 style=\"margin-bottom: 15px;\">Connection Status</h2>\n");
        html.append("            <div class=\"metric-row\"><span class=\"metric-label\">Active Connections:</span><span class=\"metric-value\">").append(registry.getActiveCount()).append("</span></div>\n");
        html.append("            <div class=\"metric-row\"><span class=\"metric-label\">Zombies Killed:</span><span class=\"metric-value\">").append(reaper.getZombiesKilled()).append("</span></div>\n");
        html.append("            <div class=\"metric-row\"><span class=\"metric-label\">Reaper Status:</span><span class=\"metric-value status-").append(reaper.isRunning() ? "running" : "stopped").append("\">").append(reaper.isRunning() ? "🟢 Running" : "🔴 Stopped").append("</span></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"card\">\n");
        html.append("            <h2 style=\"margin-bottom: 15px;\">Timeout Wheel Status</h2>\n");
        html.append("            <div class=\"metric-row\"><span class=\"metric-label\">Current Slot:</span><span class=\"metric-value\">").append(wheelStats.currentSlot()).append(" / 60</span></div>\n");
        html.append("            <div class=\"metric-row\"><span class=\"metric-label\">Wheel Active Connections:</span><span class=\"metric-value\">").append(wheelStats.activeConnections()).append("</span></div>\n");
        html.append("            <div class=\"metric-row\"><span class=\"metric-label\">Total Scheduled:</span><span class=\"metric-value\">").append(wheelStats.totalScheduled()).append("</span></div>\n");
        html.append("            <div class=\"metric-row\"><span class=\"metric-label\">Total Expired:</span><span class=\"metric-value\">").append(wheelStats.totalExpired()).append("</span></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"card\">\n");
        html.append("            <h2 style=\"margin-bottom: 15px;\">Metrics</h2>\n");
        html.append("            <div class=\"metric-row\"><span class=\"metric-label\">Heartbeats Received:</span><span class=\"metric-value\">").append(metricsSnapshot.heartbeatsReceived()).append("</span></div>\n");
        html.append("            <div class=\"metric-row\"><span class=\"metric-label\">Heartbeats Sent:</span><span class=\"metric-value\">").append(metricsSnapshot.heartbeatsSent()).append("</span></div>\n");
        html.append("            <div class=\"metric-row\"><span class=\"metric-label\">Connections Accepted:</span><span class=\"metric-value\">").append(metricsSnapshot.connectionsAccepted()).append("</span></div>\n");
        html.append("            <div class=\"metric-row\"><span class=\"metric-label\">Connections Closed:</span><span class=\"metric-value\">").append(metricsSnapshot.connectionsClosed()).append("</span></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"card\">\n");
        html.append("            <h2 style=\"margin-bottom: 15px;\">Quick Actions</h2>\n");
        html.append("            <p style=\"margin-bottom: 10px;\"><a href=\"/dashboard\" class=\"link\">📊 Open Dashboard</a></p>\n");
        html.append("            <p style=\"margin-bottom: 10px;\"><a href=\"/metrics\" class=\"link\">📈 View Metrics JSON</a></p>\n");
        html.append("            <p style=\"margin-top: 20px; opacity: 0.8; font-size: 0.9rem;\">Status page auto-refreshes every 5 seconds</p>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("    <script>setInterval(() => { location.reload(); }, 5000);</script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    public static void main(String[] args) throws IOException {
        FluxGateway gateway = new FluxGateway(8080);
        gateway.start();
        
        // Keep server running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            gateway.stop();
        }
    }
}
