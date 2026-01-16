package com.flux.gateway.concurrency.dashboard;

import com.flux.gateway.concurrency.common.ServerInterface;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class DashboardServer {
    private final HttpServer httpServer;
    private ServerInterface activeServer;
    
    public DashboardServer(int port) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        setupRoutes();
    }
    
    private void setupRoutes() {
        httpServer.createContext("/", exchange -> {
            String html = generateDashboardHtml();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        
        httpServer.createContext("/metrics", exchange -> {
            String json = activeServer != null 
                ? activeServer.getMetrics().snapshot().toJson()
                : "{\"error\":\"No active server\"}";
            
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }
    
    public void setActiveServer(ServerInterface server) {
        this.activeServer = server;
    }
    
    public void start() {
        httpServer.start();
        System.out.println("Dashboard available at http://localhost:" + httpServer.getAddress().getPort());
    }
    
    public void stop() {
        httpServer.stop(0);
    }
    
    private String generateDashboardHtml() {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Flux Day 7: Concurrency Models</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: 'Segoe UI', system-ui, sans-serif;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    min-height: 100vh;
                    padding: 20px;
                    color: #333;
                }
                .container {
                    max-width: 1400px;
                    margin: 0 auto;
                }
                header {
                    background: white;
                    padding: 30px;
                    border-radius: 15px;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                    margin-bottom: 30px;
                }
                h1 {
                    font-size: 2.5em;
                    background: linear-gradient(135deg, #667eea, #764ba2);
                    -webkit-background-clip: text;
                    -webkit-text-fill-color: transparent;
                    margin-bottom: 10px;
                }
                .subtitle {
                    color: #666;
                    font-size: 1.1em;
                }
                .metrics-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                    gap: 20px;
                    margin-bottom: 30px;
                }
                .metric-card {
                    background: white;
                    padding: 25px;
                    border-radius: 12px;
                    box-shadow: 0 5px 15px rgba(0,0,0,0.1);
                    transition: transform 0.2s, box-shadow 0.2s;
                }
                .metric-card:hover {
                    transform: translateY(-5px);
                    box-shadow: 0 10px 25px rgba(0,0,0,0.15);
                }
                .metric-value {
                    font-size: 2.5em;
                    font-weight: bold;
                    color: #667eea;
                    margin: 10px 0;
                }
                .metric-label {
                    color: #888;
                    font-size: 0.9em;
                    text-transform: uppercase;
                    letter-spacing: 1px;
                }
                .controls {
                    background: white;
                    padding: 30px;
                    border-radius: 12px;
                    box-shadow: 0 5px 15px rgba(0,0,0,0.1);
                    margin-bottom: 30px;
                }
                .button-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 15px;
                    margin-top: 20px;
                }
                button {
                    padding: 15px 25px;
                    font-size: 1em;
                    font-weight: 600;
                    border: none;
                    border-radius: 8px;
                    cursor: pointer;
                    transition: all 0.3s;
                    box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                }
                .btn-thread {
                    background: linear-gradient(135deg, #f093fb, #f5576c);
                    color: white;
                }
                .btn-nio {
                    background: linear-gradient(135deg, #4facfe, #00f2fe);
                    color: white;
                }
                .btn-virtual {
                    background: linear-gradient(135deg, #43e97b, #38f9d7);
                    color: white;
                }
                .btn-stop {
                    background: linear-gradient(135deg, #fa709a, #fee140);
                    color: white;
                }
                button:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 6px 12px rgba(0,0,0,0.15);
                }
                button:active {
                    transform: translateY(0);
                }
                .status {
                    background: white;
                    padding: 20px;
                    border-radius: 12px;
                    box-shadow: 0 5px 15px rgba(0,0,0,0.1);
                    margin-top: 20px;
                }
                .status-indicator {
                    display: inline-block;
                    width: 12px;
                    height: 12px;
                    border-radius: 50%;
                    margin-right: 8px;
                    animation: pulse 2s infinite;
                }
                .status-active { background: #43e97b; }
                .status-inactive { background: #ccc; }
                @keyframes pulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.5; }
                }
                .chart {
                    background: white;
                    padding: 30px;
                    border-radius: 12px;
                    box-shadow: 0 5px 15px rgba(0,0,0,0.1);
                    height: 300px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <header>
                    <h1>🚀 Flux Day 7: Concurrency Models</h1>
                    <p class="subtitle">Thread-per-Connection vs NIO Reactor vs Virtual Threads</p>
                </header>
                
                <div class="metrics-grid">
                    <div class="metric-card">
                        <div class="metric-label">Active Connections</div>
                        <div class="metric-value" id="activeConnections">0</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-label">Total Connections</div>
                        <div class="metric-value" id="totalConnections">0</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-label">Messages/Sec</div>
                        <div class="metric-value" id="messagesPerSec">0</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-label">Bytes Received</div>
                        <div class="metric-value" id="bytesReceived">0</div>
                    </div>
                </div>
                
                <div class="controls">
                    <h2>Test Scenarios</h2>
                    <p style="margin: 15px 0; color: #666;">
                        Choose a server model and connection count. Monitor metrics to see performance differences.
                    </p>
                    <div class="button-grid">
                        <button class="btn-thread" onclick="startTest('thread', 1000)">
                            Thread-per-Connection<br>(1k connections)
                        </button>
                        <button class="btn-thread" onclick="startTest('thread', 10000)">
                            Thread-per-Connection<br>(10k - will crash!)
                        </button>
                        <button class="btn-nio" onclick="startTest('nio', 10000)">
                            NIO Reactor<br>(10k connections)
                        </button>
                        <button class="btn-nio" onclick="startTest('nio', 50000)">
                            NIO Reactor<br>(50k connections)
                        </button>
                        <button class="btn-virtual" onclick="startTest('virtual', 10000)">
                            Virtual Threads<br>(10k connections)
                        </button>
                        <button class="btn-virtual" onclick="startTest('virtual', 50000)">
                            Virtual Threads<br>(50k connections)
                        </button>
                    </div>
                </div>
                
                <div class="status">
                    <h3>Server Status</h3>
                    <p style="margin-top: 10px;">
                        <span class="status-indicator status-inactive" id="statusIndicator"></span>
                        <span id="statusText">No server running. Click a button above to start a test.</span>
                    </p>
                </div>
            </div>
            
            <script>
                let updateInterval;
                
                function startTest(type, connections) {
                    document.getElementById('statusIndicator').className = 'status-indicator status-active';
                    document.getElementById('statusText').textContent = 
                        `Running ${type} server with ${connections.toLocaleString()} connections...`;
                    
                    alert(`Manual Test Required:\\n\\n` +
                          `1. Open terminal in project directory\\n` +
                          `2. Run: ./demo.sh ${type} ${connections}\\n` +
                          `3. Metrics will update automatically`);
                    
                    startMetricsUpdate();
                }
                
                function startMetricsUpdate() {
                    if (updateInterval) clearInterval(updateInterval);
                    
                    updateInterval = setInterval(async () => {
                        try {
                            const response = await fetch('/metrics');
                            const data = await response.json();
                            
                            if (data.error) return;
                            
                            document.getElementById('activeConnections').textContent = 
                                data.activeConnections.toLocaleString();
                            document.getElementById('totalConnections').textContent = 
                                data.totalConnections.toLocaleString();
                            document.getElementById('messagesPerSec').textContent = 
                                data.messagesPerSec.toFixed(0);
                            document.getElementById('bytesReceived').textContent = 
                                formatBytes(data.bytesReceived);
                        } catch (e) {
                            console.error('Metrics fetch failed:', e);
                        }
                    }, 1000);
                }
                
                function formatBytes(bytes) {
                    if (bytes < 1024) return bytes + ' B';
                    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
                    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
                    return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
                }
                
                // Start metrics update on page load
                startMetricsUpdate();
            </script>
        </body>
        </html>
        """;
    }
}
