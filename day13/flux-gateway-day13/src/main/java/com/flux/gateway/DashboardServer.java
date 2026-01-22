package com.flux.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DashboardServer {
    private final int port;
    private final GatewayServer gateway;
    private final ScheduledExecutorService sseExecutor;
    
    public DashboardServer(int port, GatewayServer gateway) {
        this.port = port;
        this.gateway = gateway;
        this.sseExecutor = Executors.newScheduledThreadPool(2);
    }
    
    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            
            server.createContext("/dashboard", exchange -> {
                String html = generateDashboard();
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, html.length());
                OutputStream os = exchange.getResponseBody();
                os.write(html.getBytes(StandardCharsets.UTF_8));
                os.close();
            });
            
            server.createContext("/api/stats", exchange -> {
                String json = generateStats();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, json.length());
                OutputStream os = exchange.getResponseBody();
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.close();
            });
            
            // Detailed metrics endpoint
            server.createContext("/api/metrics", exchange -> {
                String json = generateDetailedMetrics();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, json.length());
                OutputStream os = exchange.getResponseBody();
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.close();
            });
            
            // Server-Sent Events endpoint for real-time metrics
            server.createContext("/api/events", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.getResponseHeaders().add("Cache-Control", "no-cache");
                exchange.getResponseHeaders().add("Connection", "keep-alive");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, 0);
                
                OutputStream os = exchange.getResponseBody();
                final boolean[] isConnected = {true};
                
                // Send initial connection message
                try {
                    os.write(("data: " + generateStats() + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException e) {
                    isConnected[0] = false;
                    try { os.close(); } catch (IOException ignored) {}
                    return;
                }
                
                // Schedule periodic updates
                Runnable updateTask = () -> {
                    if (!isConnected[0]) {
                        return;
                    }
                    try {
                        String json = generateStats();
                        os.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    } catch (IOException e) {
                        // Client disconnected
                        isConnected[0] = false;
                        try {
                            os.close();
                        } catch (IOException ignored) {}
                    }
                };
                
                sseExecutor.scheduleAtFixedRate(updateTask, 1, 1, TimeUnit.SECONDS);
            });
            
            // Root path redirects to dashboard
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/") || path.isEmpty()) {
                    exchange.getResponseHeaders().add("Location", "/dashboard");
                    exchange.sendResponseHeaders(302, 0);
                    exchange.close();
                } else {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.close();
                }
            });
            
            server.start();
            System.out.println("[INFO] Dashboard server started on port " + port);
            System.out.println("[INFO] SSE endpoint available at http://localhost:" + port + "/api/events");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void shutdown() {
        sseExecutor.shutdown();
    }
    
    private String generateStats() {
        ReplayBufferManager mgr = gateway.getBufferManager();
        return String.format(
            "{\"activeConnections\":%d,\"activeBuffers\":%d,\"totalMessages\":%d,\"totalReplays\":%d,\"evictions\":%d}",
            gateway.getActiveConnections(),
            mgr.getActiveBufferCount(),
            mgr.getTotalMessagesBuffered(),
            mgr.getTotalReplays(),
            mgr.getEvictionCount()
        );
    }
    
    private String generateDetailedMetrics() {
        ReplayBufferManager mgr = gateway.getBufferManager();
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        int processors = runtime.availableProcessors();
        int threadCount = Thread.activeCount();
        
        return String.format(
            "{\"jvm\":{\"usedMemory\":%d,\"totalMemory\":%d,\"maxMemory\":%d,\"freeMemory\":%d,\"processors\":%d,\"threadCount\":%d}," +
            "\"buffer\":{\"activeBuffers\":%d,\"totalMessages\":%d,\"totalReplays\":%d,\"evictions\":%d,\"bufferCapacity\":256}," +
            "\"connections\":{\"active\":%d}}",
            usedMemory, totalMemory, maxMemory, freeMemory, processors, threadCount,
            mgr.getActiveBufferCount(), mgr.getTotalMessagesBuffered(), mgr.getTotalReplays(), mgr.getEvictionCount(),
            gateway.getActiveConnections()
        );
    }
    
    private String generateDashboard() {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Flux Gateway - Replay Buffer Dashboard</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { font-family: 'Segoe UI', system-ui, sans-serif; background: #f5f7fa; color: #2d3748; padding: 20px; }
                .header { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); padding: 30px; border-radius: 12px; margin-bottom: 30px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); }
                .header h1 { font-size: 32px; margin-bottom: 8px; color: white; }
                .header p { opacity: 0.95; font-size: 14px; color: white; }
                .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin-bottom: 30px; }
                .metric { background: white; padding: 25px; border-radius: 12px; border-left: 4px solid #4facfe; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); }
                .metric-label { font-size: 13px; color: #718096; margin-bottom: 8px; text-transform: uppercase; letter-spacing: 1px; }
                .metric-value { font-size: 36px; font-weight: 700; color: #4facfe; }
                .controls { background: white; padding: 25px; border-radius: 12px; margin-bottom: 30px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); }
                .controls h2 { margin-bottom: 15px; font-size: 20px; color: #2d3748; }
                button { background: #4facfe; color: white; border: none; padding: 12px 24px; border-radius: 6px; cursor: pointer; margin-right: 10px; font-size: 14px; font-weight: 600; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); }
                button:hover { background: #3d8bfe; }
                .ring-buffer { background: white; padding: 25px; border-radius: 12px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); }
                .ring-buffer h2 { margin-bottom: 20px; font-size: 20px; color: #2d3748; }
                .ring-visual { display: flex; gap: 4px; flex-wrap: wrap; }
                .slot { width: 40px; height: 40px; background: #e2e8f0; border-radius: 4px; display: flex; align-items: center; justify-content: center; font-size: 11px; transition: all 0.3s; }
                .slot.filled { background: #4facfe; color: white; }
                .metrics-panel { background: white; padding: 25px; border-radius: 12px; margin-bottom: 30px; display: none; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); }
                .metrics-panel.visible { display: block; }
                .metrics-panel h2 { margin-bottom: 20px; font-size: 20px; display: flex; justify-content: space-between; align-items: center; color: #2d3748; }
                .metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; }
                .metric-item { background: #f7fafc; padding: 15px; border-radius: 8px; border: 1px solid #e2e8f0; }
                .metric-item-label { font-size: 12px; color: #718096; margin-bottom: 5px; text-transform: uppercase; }
                .metric-item-value { font-size: 18px; font-weight: 600; color: #4facfe; }
                .close-btn { background: #ff4757; padding: 8px 16px; font-size: 12px; }
                .close-btn:hover { background: #ff3838; }
                .progress-bar { width: 100%; height: 8px; background: #e2e8f0; border-radius: 4px; overflow: hidden; margin-top: 8px; }
                .progress-fill { height: 100%; background: linear-gradient(90deg, #4facfe, #00f2fe); transition: width 0.3s; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>Flux Gateway - Replay Buffer</h1>
                <p>Real-time Replay Buffer Monitoring</p>
            </div>
            
            <div class="grid">
                <div class="metric">
                    <div class="metric-label">Active Connections</div>
                    <div class="metric-value" id="connections">0</div>
                </div>
                <div class="metric">
                    <div class="metric-label">Active Buffers</div>
                    <div class="metric-value" id="buffers">0</div>
                </div>
                <div class="metric">
                    <div class="metric-label">Total Messages</div>
                    <div class="metric-value" id="messages">0</div>
                </div>
                <div class="metric">
                    <div class="metric-label">Replay Events</div>
                    <div class="metric-value" id="replays">0</div>
                </div>
            </div>
            
            <div class="controls">
                <h2>Simulation Controls</h2>
                <button onclick="alert('Run ./demo.sh in terminal')">Start Load Test</button>
                <button onclick="toggleMetrics()">Toggle Detailed Metrics</button>
            </div>
            
            <div class="metrics-panel" id="metricsPanel">
                <h2>
                    <span>Detailed System Metrics</span>
                    <button class="close-btn" onclick="toggleMetrics()">Close</button>
                </h2>
                <div class="metrics-grid" id="metricsGrid">
                    <div class="metric-item">
                        <div class="metric-item-label">JVM Used Memory</div>
                        <div class="metric-item-value" id="jvmUsed">0 MB</div>
                        <div class="progress-bar"><div class="progress-fill" id="jvmUsedBar" style="width: 0%"></div></div>
                    </div>
                    <div class="metric-item">
                        <div class="metric-item-label">JVM Total Memory</div>
                        <div class="metric-item-value" id="jvmTotal">0 MB</div>
                    </div>
                    <div class="metric-item">
                        <div class="metric-item-label">JVM Max Memory</div>
                        <div class="metric-item-value" id="jvmMax">0 MB</div>
                    </div>
                    <div class="metric-item">
                        <div class="metric-item-label">JVM Free Memory</div>
                        <div class="metric-item-value" id="jvmFree">0 MB</div>
                    </div>
                    <div class="metric-item">
                        <div class="metric-item-label">CPU Processors</div>
                        <div class="metric-item-value" id="processors">0</div>
                    </div>
                    <div class="metric-item">
                        <div class="metric-item-label">Active Threads</div>
                        <div class="metric-item-value" id="threadCount">0</div>
                    </div>
                    <div class="metric-item">
                        <div class="metric-item-label">Buffer Capacity</div>
                        <div class="metric-item-value" id="bufferCapacity">256</div>
                    </div>
                    <div class="metric-item">
                        <div class="metric-item-label">Memory Usage %</div>
                        <div class="metric-item-value" id="memoryPercent">0%</div>
                    </div>
                </div>
            </div>
            
            <div class="ring-buffer">
                <h2>Ring Buffer Visualization (First Buffer)</h2>
                <div class="ring-visual" id="ring"></div>
            </div>
            
            <script>
                // Server-Sent Events (SSE) approach for real-time updates
                const eventSource = new EventSource('/api/events');
                
                eventSource.onmessage = function(event) {
                    try {
                        const data = JSON.parse(event.data);
                        document.getElementById('connections').textContent = data.activeConnections;
                        document.getElementById('buffers').textContent = data.activeBuffers;
                        document.getElementById('messages').textContent = data.totalMessages;
                        document.getElementById('replays').textContent = data.totalReplays;
                        
                        updateRingVisualization(data.totalMessages % 256);
                        
                        // Update detailed metrics if panel is visible
                        if (document.getElementById('metricsPanel').classList.contains('visible')) {
                            updateDetailedMetrics();
                        }
                    } catch (error) {
                        console.error('Error parsing SSE data:', error);
                    }
                };
                
                eventSource.onerror = function(error) {
                    console.error('SSE connection error:', error);
                    // Fallback to polling if SSE fails
                    eventSource.close();
                    console.log('Falling back to polling...');
                    startPolling();
                };
                
                // Fallback polling function
                function startPolling() {
                    function updateStats() {
                        fetch('/api/stats')
                            .then(r => {
                                if (!r.ok) {
                                    throw new Error('HTTP error! status: ' + r.status);
                                }
                                return r.json();
                            })
                            .then(data => {
                                document.getElementById('connections').textContent = data.activeConnections;
                                document.getElementById('buffers').textContent = data.activeBuffers;
                                document.getElementById('messages').textContent = data.totalMessages;
                                document.getElementById('replays').textContent = data.totalReplays;
                                
                                updateRingVisualization(data.totalMessages % 256);
                            })
                            .catch(error => {
                                console.error('Error fetching stats:', error);
                            });
                    }
                    setInterval(updateStats, 1000);
                    updateStats();
                }
                
                function toggleMetrics() {
                    const panel = document.getElementById('metricsPanel');
                    panel.classList.toggle('visible');
                    if (panel.classList.contains('visible')) {
                        updateDetailedMetrics();
                        // Update metrics every second when visible
                        if (!window.metricsInterval) {
                            window.metricsInterval = setInterval(updateDetailedMetrics, 1000);
                        }
                    } else {
                        if (window.metricsInterval) {
                            clearInterval(window.metricsInterval);
                            window.metricsInterval = null;
                        }
                    }
                }
                
                function updateDetailedMetrics() {
                    fetch('/api/metrics')
                        .then(r => r.json())
                        .then(data => {
                            // Format bytes to MB
                            const formatMB = (bytes) => (bytes / (1024 * 1024)).toFixed(2);
                            
                            document.getElementById('jvmUsed').textContent = formatMB(data.jvm.usedMemory) + ' MB';
                            document.getElementById('jvmTotal').textContent = formatMB(data.jvm.totalMemory) + ' MB';
                            document.getElementById('jvmMax').textContent = formatMB(data.jvm.maxMemory) + ' MB';
                            document.getElementById('jvmFree').textContent = formatMB(data.jvm.freeMemory) + ' MB';
                            document.getElementById('processors').textContent = data.jvm.processors;
                            document.getElementById('threadCount').textContent = data.jvm.threadCount;
                            document.getElementById('bufferCapacity').textContent = data.buffer.bufferCapacity;
                            
                            // Calculate memory usage percentage
                            const memoryPercent = (data.jvm.usedMemory / data.jvm.maxMemory * 100).toFixed(1);
                            document.getElementById('memoryPercent').textContent = memoryPercent + '%';
                            document.getElementById('jvmUsedBar').style.width = memoryPercent + '%';
                        })
                        .catch(error => {
                            console.error('Error fetching detailed metrics:', error);
                        });
                }
                
                function updateRingVisualization(filled) {
                    const ring = document.getElementById('ring');
                    if (ring.children.length === 0) {
                        for (let i = 0; i < 256; i++) {
                            const slot = document.createElement('div');
                            slot.className = 'slot';
                            slot.id = 'slot-' + i;
                            ring.appendChild(slot);
                        }
                    }
                    
                    for (let i = 0; i < 256; i++) {
                        const slot = document.getElementById('slot-' + i);
                        if (i < filled) {
                            slot.classList.add('filled');
                            slot.textContent = i;
                        } else {
                            slot.classList.remove('filled');
                            slot.textContent = '';
                        }
                    }
                }
            </script>
        </body>
        </html>
        """;
    }
}
