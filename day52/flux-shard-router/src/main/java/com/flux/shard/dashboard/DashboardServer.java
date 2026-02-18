package com.flux.shard.dashboard;

import com.flux.shard.gateway.ShardDistributionTracker;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP server for real-time shard distribution visualization.
 * Uses com.sun.net.httpserver (built into JDK).
 */
public class DashboardServer {
    
    private final HttpServer server;
    private final ShardDistributionTracker tracker;
    private final int totalShards;
    
    public DashboardServer(int port, ShardDistributionTracker tracker, int totalShards) 
            throws IOException {
        this.tracker = tracker;
        this.totalShards = totalShards;
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        
        server.createContext("/", this::handleRoot);
        server.createContext("/health", this::handleHealth);
        server.createContext("/api/shards", this::handleShardData);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/reset", this::handleReset);
        
        server.setExecutor(Executors.newCachedThreadPool());
    }
    
    public void start() {
        server.start();
        int p = server.getAddress().getPort();
        System.out.println("Dashboard running at http://localhost:" + p);
        System.out.println("  (From Windows browser, also try http://127.0.0.1:" + p + " or your WSL IP)");
    }
    
    public void stop() {
        server.stop(0);
    }
    
    private void handleRoot(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            consumeRequestBody(exchange);
            String html = generateDashboardHTML();
            sendResponse(exchange, 200, "text/html", html);
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "text/plain", "Error: " + e.getMessage());
        }
    }
    
    private void consumeRequestBody(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try (var is = exchange.getRequestBody()) {
            is.readAllBytes();
        }
    }
    
    private void handleHealth(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        consumeRequestBody(exchange);
        sendResponse(exchange, 200, "text/plain", "OK");
    }
    
    private void handleShardData(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        consumeRequestBody(exchange);
        long[] counts = tracker.getShardCounts();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < counts.length; i++) {
            if (i > 0) json.append(",");
            json.append(counts[i]);
        }
        json.append("]");
        sendResponse(exchange, 200, "application/json", json.toString());
    }
    
    private void handleStats(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        consumeRequestBody(exchange);
        var stats = tracker.getStats();
        String json = String.format(
            """
            {
                "mean": %.2f,
                "stdDev": %.2f,
                "min": %d,
                "max": %d,
                "cv": %.2f,
                "maxDeviation": %.2f
            }
            """,
            stats.mean(), stats.stdDev(), stats.min(), stats.max(),
            stats.coefficientOfVariation(), stats.maxDeviation()
        );
        sendResponse(exchange, 200, "application/json", json);
    }
    
    private void handleReset(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        consumeRequestBody(exchange);
        tracker.reset();
        sendResponse(exchange, 200, "application/json", "{\"status\":\"reset\"}");
    }
    
    private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, 
                             int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private String generateDashboardHTML() {
        return String.format("""
        <!DOCTYPE html>
        <html>
        <head>
            <title>Flux Shard Distribution</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                    color: #fff;
                    padding: 20px;
                }
                .container {
                    max-width: 1400px;
                    margin: 0 auto;
                }
                h1 {
                    text-align: center;
                    margin-bottom: 10px;
                    font-size: 2.5em;
                    text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
                }
                .subtitle {
                    text-align: center;
                    margin-bottom: 30px;
                    opacity: 0.9;
                    font-size: 1.1em;
                }
                .stats-panel {
                    background: rgba(255,255,255,0.1);
                    backdrop-filter: blur(10px);
                    border-radius: 15px;
                    padding: 20px;
                    margin-bottom: 20px;
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 15px;
                }
                .stat-card {
                    background: rgba(255,255,255,0.15);
                    padding: 15px;
                    border-radius: 10px;
                    text-align: center;
                }
                .stat-label {
                    font-size: 0.9em;
                    opacity: 0.8;
                    margin-bottom: 5px;
                }
                .stat-value {
                    font-size: 2em;
                    font-weight: bold;
                }
                .heatmap-container {
                    background: rgba(255,255,255,0.1);
                    backdrop-filter: blur(10px);
                    border-radius: 15px;
                    padding: 30px;
                    margin-bottom: 20px;
                }
                .heatmap {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
                    gap: 10px;
                    margin-top: 20px;
                }
                .shard-cell {
                    aspect-ratio: 1;
                    border-radius: 8px;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    font-size: 0.8em;
                    transition: transform 0.2s;
                    cursor: pointer;
                    border: 2px solid rgba(255,255,255,0.2);
                }
                .shard-cell:hover {
                    transform: scale(1.1);
                    z-index: 10;
                }
                .shard-id {
                    font-weight: bold;
                    margin-bottom: 5px;
                }
                .shard-count {
                    font-size: 0.9em;
                    opacity: 0.9;
                }
                .controls {
                    text-align: center;
                    margin-top: 20px;
                }
                button {
                    background: rgba(255,255,255,0.2);
                    border: 2px solid rgba(255,255,255,0.3);
                    color: white;
                    padding: 12px 30px;
                    font-size: 1em;
                    border-radius: 25px;
                    cursor: pointer;
                    margin: 0 10px;
                    transition: all 0.3s;
                }
                button:hover {
                    background: rgba(255,255,255,0.3);
                    transform: translateY(-2px);
                }
                .legend {
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    gap: 20px;
                    margin-top: 20px;
                }
                .legend-item {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .legend-color {
                    width: 30px;
                    height: 30px;
                    border-radius: 5px;
                    border: 2px solid rgba(255,255,255,0.3);
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>Flux Shard Distribution</h1>
                <div class="subtitle">Real-time visualization of Discord-style guild sharding</div>
                
                <div class="stats-panel">
                    <div class="stat-card">
                        <div class="stat-label">Mean Events/Shard</div>
                        <div class="stat-value" id="mean">0</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-label">Std Deviation</div>
                        <div class="stat-value" id="stdDev">0</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-label">Coefficient of Variation</div>
                        <div class="stat-value" id="cv">0%%</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-label">Max Deviation</div>
                        <div class="stat-value" id="maxDev">0x</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-label">Min Load</div>
                        <div class="stat-value" id="min">0</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-label">Max Load</div>
                        <div class="stat-value" id="max">0</div>
                    </div>
                </div>
                
                <div class="heatmap-container">
                    <h2 style="text-align: center;">Shard Load Heatmap (%d shards)</h2>
                    <div class="heatmap" id="heatmap"></div>
                    <div class="legend">
                        <div class="legend-item">
                            <div class="legend-color" style="background: #3b82f6;"></div>
                            <span>Low Load</span>
                        </div>
                        <div class="legend-item">
                            <div class="legend-color" style="background: #f59e0b;"></div>
                            <span>Medium Load</span>
                        </div>
                        <div class="legend-item">
                            <div class="legend-color" style="background: #ef4444;"></div>
                            <span>High Load</span>
                        </div>
                    </div>
                </div>
                
                <div class="controls">
                    <button onclick="resetCounters()">Reset Counters</button>
                    <button onclick="toggleAutoRefresh()">Pause Updates</button>
                </div>
            </div>
            
            <script>
                const TOTAL_SHARDS = %d;
                let autoRefresh = true;
                let refreshInterval;
                
                // Initialize heatmap grid
                const heatmap = document.getElementById('heatmap');
                for (let i = 0; i < TOTAL_SHARDS; i++) {
                    const cell = document.createElement('div');
                    cell.className = 'shard-cell';
                    cell.innerHTML = '<div class="shard-id">Shard ' + i + '</div><div class="shard-count" id="count-' + i + '">0</div>';
                    cell.style.background = '#3b82f6';
                    heatmap.appendChild(cell);
                }
                
                async function updateDashboard() {
                    try {
                        const [shardData, statsData] = await Promise.all([
                            fetch('/api/shards').then(r => r.json()),
                            fetch('/api/stats').then(r => r.json())
                        ]);
                        
                        updateStats(statsData);
                        updateHeatmap(shardData, statsData.max);
                    } catch (err) {
                        console.error('Update failed:', err);
                    }
                }
                
                function updateStats(stats) {
                    document.getElementById('mean').textContent = stats.mean.toFixed(0);
                    document.getElementById('stdDev').textContent = stats.stdDev.toFixed(1);
                    document.getElementById('cv').textContent = stats.cv.toFixed(1) + '%%';
                    document.getElementById('maxDev').textContent = stats.maxDeviation.toFixed(2) + 'x';
                    document.getElementById('min').textContent = stats.min;
                    document.getElementById('max').textContent = stats.max;
                }
                
                function updateHeatmap(counts, maxCount) {
                    counts.forEach((count, i) => {
                        const cell = heatmap.children[i];
                        const countEl = document.getElementById('count-' + i);
                        countEl.textContent = count.toLocaleString();
                        
                        // Color intensity based on load
                        const intensity = maxCount > 0 ? count / maxCount : 0;
                        let color;
                        if (intensity < 0.4) {
                            color = '#3b82f6'; // Blue - low
                        } else if (intensity < 0.7) {
                            color = '#f59e0b'; // Orange - medium
                        } else {
                            color = '#ef4444'; // Red - high
                        }
                        cell.style.background = color;
                        cell.style.opacity = 0.5 + (intensity * 0.5);
                    });
                }
                
                async function resetCounters() {
                    await fetch('/api/reset', { method: 'POST' });
                    updateDashboard();
                }
                
                function toggleAutoRefresh() {
                    autoRefresh = !autoRefresh;
                    const btn = event.target;
                    if (autoRefresh) {
                        btn.textContent = 'Pause Updates';
                        startAutoRefresh();
                    } else {
                        btn.textContent = 'Resume Updates';
                        if (refreshInterval) clearInterval(refreshInterval);
                    }
                }
                
                function startAutoRefresh() {
                    refreshInterval = setInterval(updateDashboard, 500);
                }
                
                // Initial update and start auto-refresh
                updateDashboard();
                startAutoRefresh();
            </script>
        </body>
        </html>
        """, totalShards, totalShards);
    }
}
