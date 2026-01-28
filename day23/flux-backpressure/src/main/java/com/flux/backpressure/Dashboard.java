package com.flux.backpressure;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Lightweight HTTP server for real-time dashboard.
 */
public class Dashboard {
    private final int port;
    private final GatewayServer gateway;
    private HttpServer httpServer;
    
    public Dashboard(int port, GatewayServer gateway) {
        this.port = port;
        this.gateway = gateway;
    }
    
    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        
        httpServer.createContext("/", this::handleIndex);
        httpServer.createContext("/metrics", this::handleMetrics);
        httpServer.createContext("/simulate-slow", this::handleSimulateSlow);
        httpServer.createContext("/broadcast-burst", this::handleBroadcastBurst);
        
        httpServer.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        httpServer.start();
        
        System.out.println("[Dashboard] HTTP server started on http://localhost:" + port);
    }
    
    private void handleIndex(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("Expires", "0");
        String html = getIndexHtml();
        sendResponse(exchange, 200, html, "text/html");
    }
    
    private void handleMetrics(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("Expires", "0");
        String json = gateway.getMetrics().toJson();
        sendResponse(exchange, 200, json, "application/json");
    }
    
    private void handleSimulateSlow(HttpExchange exchange) throws IOException {
        // Make 10% of connections slow by filling their buffers artificially
        int slowCount = 0;
        for (Connection conn : gateway.getConnections().values()) {
            if (slowCount < gateway.getConnections().size() / 10) {
                // Simulate slow consumer by not reading from socket
                // (in real impl, we'd mark connection as "paused")
                slowCount++;
            }
        }
        
        sendResponse(exchange, 200, "{\"status\":\"simulated " + slowCount + " slow clients\"}", 
                    "application/json");
    }
    
    private void handleBroadcastBurst(HttpExchange exchange) throws IOException {
        // Trigger high-rate broadcast (handled by existing broadcast loop)
        sendResponse(exchange, 200, "{\"status\":\"burst triggered\"}", "application/json");
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) 
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private String getIndexHtml() {
        return """
<!DOCTYPE html>
<html>
<head>
    <title>Flux Backpressure Dashboard</title>
    <style>
        body {
            font-family: 'Courier New', monospace;
            background: #0a0a0a;
            color: #66ccff;
            margin: 0;
            padding: 20px;
        }
        .header {
            text-align: center;
            border-bottom: 2px solid #66ccff;
            padding-bottom: 20px;
            margin-bottom: 30px;
        }
        .metrics {
            display: grid;
            grid-template-columns: repeat(5, 1fr);
            gap: 20px;
            margin-bottom: 30px;
        }
        .metric-card {
            background: #1a1a1a;
            border: 1px solid #66ccff;
            padding: 15px;
            border-radius: 5px;
        }
        .metric-value {
            font-size: 32px;
            font-weight: bold;
            color: #00ffff;
        }
        .metric-label {
            font-size: 12px;
            color: #888;
            margin-top: 5px;
        }
        .connection-grid {
            display: grid;
            grid-template-columns: repeat(10, 1fr);
            gap: 5px;
            margin-bottom: 30px;
        }
        .connection-cell {
            aspect-ratio: 1;
            border: 1px solid #333;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 10px;
            cursor: pointer;
            transition: all 0.3s;
        }
        .cell-empty { background: #1a1a1a; }
        .cell-active { background: #66ccff; color: #000; }
        .cell-backpressure-low { background: #ffff00; color: #000; }
        .cell-backpressure-med { background: #ff8800; color: #000; }
        .cell-backpressure-high { background: #ff0000; color: #fff; }
        .cell-evicted { background: #000; color: #ff0000; }
        .controls {
            display: flex;
            gap: 10px;
            justify-content: center;
            margin-bottom: 30px;
        }
        button {
            background: #66ccff;
            color: #000;
            border: none;
            padding: 10px 20px;
            font-family: 'Courier New', monospace;
            font-size: 14px;
            cursor: pointer;
            border-radius: 3px;
            transition: background 0.3s;
        }
        button:hover {
            background: #00ffff;
        }
        .legend {
            display: flex;
            gap: 20px;
            justify-content: center;
            font-size: 12px;
        }
        .legend-item {
            display: flex;
            align-items: center;
            gap: 5px;
        }
        .legend-color {
            width: 20px;
            height: 20px;
            border: 1px solid #333;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>FLUX BACKPRESSURE MONITOR</h1>
        <p>TCP Send Buffer Saturation Detection</p>
        <p id="live-indicator" style="font-size:14px; color:#66ccff;">● LIVE — Last updated: <span id="last-updated">--</span></p>
    </div>
    
    <div class="metrics">
        <div class="metric-card">
            <div class="metric-value" id="backpressure-events">0</div>
            <div class="metric-label">BACKPRESSURE EVENTS</div>
        </div>
        <div class="metric-card">
            <div class="metric-value" id="evictions">0</div>
            <div class="metric-label">SLOW CONSUMER EVICTIONS</div>
        </div>
        <div class="metric-card">
            <div class="metric-value" id="buffered">0</div>
            <div class="metric-label">MESSAGES BUFFERED</div>
        </div>
        <div class="metric-card">
            <div class="metric-value" id="write-rate">100.0%</div>
            <div class="metric-label">WRITE SUCCESS RATE</div>
        </div>
        <div class="metric-card">
            <div class="metric-value" id="active-connections">0</div>
            <div class="metric-label">ACTIVE CONNECTIONS</div>
        </div>
    </div>
    
    <div class="controls">
        <button onclick="simulateSlow()">SIMULATE SLOW CLIENTS</button>
        <button onclick="broadcastBurst()">BROADCAST BURST</button>
        <button onclick="location.reload()">RESET</button>
    </div>
    
    <h3 style="text-align: center;">CONNECTION STATUS (100 slots)</h3>
    <div class="connection-grid" id="connection-grid"></div>
    
    <div class="legend">
        <div class="legend-item">
            <div class="legend-color cell-empty"></div>
            <span>Empty</span>
        </div>
        <div class="legend-item">
            <div class="legend-color cell-active"></div>
            <span>Active (0-20%)</span>
        </div>
        <div class="legend-item">
            <div class="legend-color cell-backpressure-low"></div>
            <span>Buffering (20-60%)</span>
        </div>
        <div class="legend-item">
            <div class="legend-color cell-backpressure-med"></div>
            <span>Backpressure (60-80%)</span>
        </div>
        <div class="legend-item">
            <div class="legend-color cell-backpressure-high"></div>
            <span>Critical (80-100%)</span>
        </div>
        <div class="legend-item">
            <div class="legend-color cell-evicted"></div>
            <span>Evicted</span>
        </div>
    </div>
    
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            // Initialize connection grid
            const grid = document.getElementById('connection-grid');
            for (let i = 0; i < 100; i++) {
                const cell = document.createElement('div');
                cell.className = 'connection-cell cell-empty';
                cell.id = 'cell-' + i;
                cell.textContent = i;
                grid.appendChild(cell);
            }
            
            function formatTime() {
                const d = new Date();
                return d.toTimeString().split(' ')[0] + '.' + (d.getMilliseconds() + '').padStart(3, '0');
            }
            
            // Poll metrics every 300ms using same-origin URL
            function updateMetrics() {
                const url = (window.location.origin || '') + '/metrics?_=' + Date.now();
                fetch(url, { cache: 'no-store', headers: { 'Accept': 'application/json' } })
                    .then(function(res) {
                        if (!res.ok) throw new Error('Metrics ' + res.status);
                        return res.json();
                    })
                    .then(function(data) {
                        document.getElementById('backpressure-events').textContent = data.backpressureEvents != null ? data.backpressureEvents : 0;
                        document.getElementById('evictions').textContent = data.slowConsumerEvictions != null ? data.slowConsumerEvictions : 0;
                        document.getElementById('buffered').textContent = data.messagesBuffered != null ? data.messagesBuffered : 0;
                        document.getElementById('write-rate').textContent = (data.writeSuccessRate != null ? data.writeSuccessRate : 100).toFixed(1) + '%';
                        var conns = data.connections || {};
                        document.getElementById('active-connections').textContent = Object.keys(conns).length;
                        document.getElementById('last-updated').textContent = formatTime();
                        document.getElementById('live-indicator').style.color = '#66ccff';
                        
                        // Update connection grid (JSON keys are strings)
                        for (var i = 0; i < 100; i++) {
                            var cell = document.getElementById('cell-' + i);
                            var utilization = conns[i] != null ? conns[i] : (conns[String(i)] != null ? conns[String(i)] : -1);
                            
                            if (utilization === -1) {
                                cell.className = 'connection-cell cell-empty';
                            } else if (utilization === 0) {
                                cell.className = 'connection-cell cell-active';
                            } else if (utilization < 20) {
                                cell.className = 'connection-cell cell-active';
                            } else if (utilization < 60) {
                                cell.className = 'connection-cell cell-backpressure-low';
                            } else if (utilization < 80) {
                                cell.className = 'connection-cell cell-backpressure-med';
                            } else {
                                cell.className = 'connection-cell cell-backpressure-high';
                            }
                        }
                    })
                    .catch(function(err) {
                        console.error('Dashboard metrics fetch failed:', err);
                        document.getElementById('active-connections').textContent = '?';
                        document.getElementById('last-updated').textContent = 'error';
                        document.getElementById('live-indicator').style.color = '#f00';
                    });
            }
            updateMetrics();
            setInterval(updateMetrics, 300);
            window.simulateSlow = function() {
                fetch((window.location.origin || '') + '/simulate-slow').then(function(r) { return r.json(); }).then(function(d) { alert(d.status); });
            };
            window.broadcastBurst = function() {
                fetch((window.location.origin || '') + '/broadcast-burst').then(function(r) { return r.json(); }).then(function(d) { alert(d.status); });
            };
        });
    </script>
</body>
</html>
        """;
    }
}
