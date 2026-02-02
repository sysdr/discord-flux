package com.flux.presence.dashboard;

import com.flux.presence.core.PresenceService;
import com.flux.presence.core.PresenceStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP handler for serving the dashboard and handling API requests.
 */
public class DashboardHandler implements HttpHandler {
    
    private final PresenceService presenceService;
    
    public DashboardHandler(PresenceService presenceService) {
        this.presenceService = presenceService;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        
        if (path.equals("/") || path.equals("/dashboard")) {
            serveDashboard(exchange);
        } else if (path.equals("/api/metrics")) {
            serveMetrics(exchange);
        } else if (path.startsWith("/api/get-presence")) {
            serveGetPresence(exchange);
        } else if (path.equals("/api/simulate-connect")) {
            simulateConnect(exchange);
        } else if (path.equals("/api/simulate-storm")) {
            simulateStorm(exchange);
        } else {
            send404(exchange);
        }
    }
    
    private void serveDashboard(HttpExchange exchange) throws IOException {
        String html = """
<!DOCTYPE html>
<html>
<head>
    <title>Flux Presence System</title>
    <style>
        body { font-family: 'Courier New', monospace; background: #0d1117; color: #c9d1d9; padding: 20px; }
        .header { font-size: 24px; margin-bottom: 20px; color: #58a6ff; }
        .metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 15px; margin-bottom: 30px; }
        .metric-card { background: #161b22; border: 1px solid #30363d; border-radius: 6px; padding: 15px; }
        .metric-label { color: #8b949e; font-size: 12px; margin-bottom: 5px; }
        .metric-value { font-size: 32px; font-weight: bold; color: #58a6ff; }
        .metric-subtext { font-size: 11px; color: #8b949e; margin-top: 5px; }
        .controls { margin-top: 20px; }
        button { background: #238636; color: white; border: none; padding: 10px 20px; margin: 5px; 
                 border-radius: 6px; cursor: pointer; font-family: monospace; font-size: 14px; }
        button:hover { background: #2ea043; }
        button.danger { background: #da3633; }
        button.danger:hover { background: #f85149; }
        .grid { display: grid; grid-template-columns: repeat(20, 30px); gap: 2px; margin-top: 20px; }
        .user-box { width: 30px; height: 30px; border: 1px solid #30363d; display: flex; align-items: center; 
                    justify-content: center; font-size: 10px; border-radius: 3px; }
        .user-box.online { background: #238636; color: white; }
        .user-box.offline { background: #161b22; color: #484f58; }
        .log { background: #0d1117; border: 1px solid #30363d; padding: 15px; margin-top: 20px; 
               height: 200px; overflow-y: scroll; font-size: 12px; border-radius: 6px; }
        .log-entry { padding: 2px 0; }
        .log-entry.info { color: #58a6ff; }
        .log-entry.success { color: #3fb950; }
        .log-entry.error { color: #f85149; }
    </style>
</head>
<body>
    <div class="header">Flux Presence System</div>
    
    <div class="metrics" id="metrics">
        <div class="metric-card">
            <div class="metric-label">L1 CACHE HIT RATE</div>
            <div class="metric-value" id="hitRate">--</div>
            <div class="metric-subtext">Target: >95%</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">REDIS WRITES</div>
            <div class="metric-value" id="redisWrites">--</div>
            <div class="metric-subtext">Batched operations</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">REDIS READS</div>
            <div class="metric-value" id="redisReads">--</div>
            <div class="metric-subtext">L1 cache misses</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">PENDING QUEUE</div>
            <div class="metric-value" id="queueSize">--</div>
            <div class="metric-subtext">Awaiting batch flush</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">REDIS ERRORS</div>
            <div class="metric-value" id="redisErrors">--</div>
            <div class="metric-subtext">Connection failures</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">ACTIVE USERS</div>
            <div class="metric-value" id="activeUsers">--</div>
            <div class="metric-subtext">Online now</div>
        </div>
    </div>
    
    <div class="controls">
        <button onclick="simulateConnect()">Simulate User Connect</button>
        <button onclick="simulateStorm()">Simulate Reconnect Storm (5000)</button>
        <button class="danger" onclick="clearLog()">Clear Log</button>
    </div>
    
    <div class="grid" id="userGrid"></div>
    
    <div class="log" id="log"></div>
    
    <script>
        let activeUserSet = new Set();
        
        function updateMetrics() {
            fetch('/api/metrics')
                .then(r => r.json())
                .then(data => {
                    document.getElementById('hitRate').textContent = data.cacheHitRate.toFixed(1) + '%';
                    document.getElementById('redisWrites').textContent = data.redisWrites.toLocaleString();
                    document.getElementById('redisReads').textContent = data.redisReads.toLocaleString();
                    document.getElementById('queueSize').textContent = data.pendingQueueSize;
                    document.getElementById('redisErrors').textContent = data.redisErrors;
                    document.getElementById('activeUsers').textContent = activeUserSet.size;
                })
                .catch(e => log('Error fetching metrics: ' + e.message, 'error'));
        }
        
        function simulateConnect() {
            fetch('/api/simulate-connect')
                .then(r => r.json())
                .then(data => {
                    activeUserSet.add(data.userId);
                    updateUserGrid();
                    log('User ' + data.userId + ' connected', 'success');
                })
                .catch(e => log('Failed to simulate connect: ' + e.message, 'error'));
        }
        
        function simulateStorm() {
            log('Starting reconnect storm (5000 users)...', 'info');
            fetch('/api/simulate-storm')
                .then(r => r.json())
                .then(data => {
                    log('Storm complete: ' + data.count + ' users reconnected in ' + data.durationMs + 'ms', 'success');
                    for (let i = 0; i < data.count; i++) {
                        activeUserSet.add(10000 + i);
                    }
                    updateUserGrid();
                })
                .catch(e => log('Storm failed: ' + e.message, 'error'));
        }
        
        function updateUserGrid() {
            const grid = document.getElementById('userGrid');
            grid.innerHTML = '';
            let count = 0;
            for (let userId of activeUserSet) {
                if (count++ >= 100) break; // Show max 100
                const box = document.createElement('div');
                box.className = 'user-box online';
                box.textContent = userId % 100;
                grid.appendChild(box);
            }
        }
        
        function log(message, type = 'info') {
            const logDiv = document.getElementById('log');
            const entry = document.createElement('div');
            entry.className = 'log-entry ' + type;
            entry.textContent = new Date().toLocaleTimeString() + ' - ' + message;
            logDiv.appendChild(entry);
            logDiv.scrollTop = logDiv.scrollHeight;
        }
        
        function clearLog() {
            document.getElementById('log').innerHTML = '';
        }
        
        // Update metrics every second
        setInterval(updateMetrics, 1000);
        updateMetrics();
        
        log('Dashboard initialized', 'info');
    </script>
</body>
</html>
""";
        
        sendResponse(exchange, 200, html, "text/html");
    }
    
    private void serveMetrics(HttpExchange exchange) throws IOException {
        var metrics = presenceService.getMetrics();
        String json = String.format(
            "{\"cacheHits\":%d,\"cacheMisses\":%d,\"cacheHitRate\":%.2f," +
            "\"redisWrites\":%d,\"redisReads\":%d,\"redisErrors\":%d,\"pendingQueueSize\":%d}",
            metrics.cacheHits(), metrics.cacheMisses(), metrics.cacheHitRate(),
            metrics.redisWrites(), metrics.redisReads(), metrics.redisErrors(),
            metrics.pendingQueueSize()
        );
        sendResponse(exchange, 200, json, "application/json");
    }
    
    private void serveGetPresence(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        long userId = 99999L;
        if (query != null && query.startsWith("userId=")) {
            try {
                userId = Long.parseLong(query.substring(7).split("&")[0]);
            } catch (NumberFormatException ignored) {}
        }
        try {
            PresenceStatus status = presenceService.getPresence(userId).get();
            String json = "{\"userId\":" + userId + ",\"status\":\"" + status.getValue() + "\"}";
            sendResponse(exchange, 200, json, "application/json");
        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
        }
    }
    
    private void simulateConnect(HttpExchange exchange) throws IOException {
        long userId = 12345 + (long)(Math.random() * 1000);
        presenceService.markOnline(userId);
        String json = "{\"userId\":" + userId + ",\"status\":\"connected\"}";
        sendResponse(exchange, 200, json, "application/json");
    }
    
    private void simulateStorm(HttpExchange exchange) throws IOException {
        long start = System.currentTimeMillis();
        int count = 5000;
        
        // Simulate 5000 users connecting
        for (int i = 0; i < count; i++) {
            presenceService.markOnline(10000 + i);
        }
        
        long duration = System.currentTimeMillis() - start;
        String json = "{\"count\":" + count + ",\"durationMs\":" + duration + "}";
        sendResponse(exchange, 200, json, "application/json");
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private void send404(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 404, "Not Found", "text/plain");
    }
}
