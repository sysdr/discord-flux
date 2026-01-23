package com.flux.gateway;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Dashboard implements Runnable {
    
    private final HttpServer server;
    private final LeakyGateway gateway;
    
    public Dashboard(int port, LeakyGateway gateway) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.gateway = gateway;
        
        server.createContext("/dashboard", this::handleDashboard);
        server.createContext("/api/metrics", this::handleMetrics);
        server.createContext("/api/dump-heap", this::handleDumpHeap);
        
        // Handle root redirect to dashboard
        server.createContext("/", exchange -> {
            if ("/".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().set("Location", "/dashboard");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });
        
        server.setExecutor(null); // Default executor
    }
    
    @Override
    public void run() {
        server.start();
        System.out.println("[Dashboard] Started on http://localhost:" + 
            server.getAddress().getPort() + "/dashboard");
    }
    
    public void shutdown() {
        server.stop(0);
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        // Handle OPTIONS request for CORS
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        
        String html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Flux Memory Profiling Dashboard</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { 
                    font-family: 'Monaco', 'Courier New', monospace;
                    background: #0a0e27;
                    color: #00ff41;
                    padding: 20px;
                }
                .container { max-width: 1400px; margin: 0 auto; }
                h1 { 
                    font-size: 24px;
                    margin-bottom: 20px;
                    border-bottom: 2px solid #00ff41;
                    padding-bottom: 10px;
                }
                .metrics-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                    gap: 15px;
                    margin-bottom: 20px;
                }
                .metric-card {
                    background: #1a1f3a;
                    border: 1px solid #00ff41;
                    border-radius: 4px;
                    padding: 15px;
                }
                .metric-label {
                    font-size: 12px;
                    color: #7f8c8d;
                    margin-bottom: 5px;
                }
                .metric-value {
                    font-size: 28px;
                    font-weight: bold;
                }
                .leak-warning {
                    background: #c0392b;
                    color: white;
                    padding: 10px;
                    border-radius: 4px;
                    margin-bottom: 15px;
                    display: none;
                }
                .chart-container {
                    background: #1a1f3a;
                    border: 1px solid #00ff41;
                    border-radius: 4px;
                    padding: 15px;
                    margin-bottom: 20px;
                }
                canvas {
                    width: 100% !important;
                    height: 250px !important;
                }
                button {
                    background: #00ff41;
                    color: #0a0e27;
                    border: none;
                    padding: 10px 20px;
                    font-family: inherit;
                    font-size: 14px;
                    cursor: pointer;
                    border-radius: 4px;
                    margin-right: 10px;
                }
                button:hover {
                    background: #00cc33;
                }
                .progress-bar {
                    width: 100%;
                    height: 20px;
                    background: #34495e;
                    border-radius: 10px;
                    overflow: hidden;
                    margin-top: 5px;
                }
                .progress-fill {
                    height: 100%;
                    background: linear-gradient(90deg, #00ff41, #00cc33);
                    transition: width 0.3s ease;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>🔥 Flux Memory Profiling Dashboard</h1>
                <div style="font-size: 12px; color: #7f8c8d; margin-bottom: 10px;">
                    Status: <span id="status">Initializing...</span> | 
                    Last Update: <span id="lastUpdate">Never</span>
                </div>
                
                <div id="leakWarning" class="leak-warning">
                    ⚠️ MEMORY LEAK DETECTED: <span id="leakDetails"></span>
                </div>
                
                <div class="metrics-grid">
                    <div class="metric-card">
                        <div class="metric-label">Active Sessions (in map)</div>
                        <div class="metric-value" id="sessionCount">0</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-label">Messages Processed</div>
                        <div class="metric-value" id="messageCount">0</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-label">Heap Used / Max</div>
                        <div class="metric-value" id="heapUsed">0 MB</div>
                        <div class="progress-bar">
                            <div class="progress-fill" id="heapProgress"></div>
                        </div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-label">DirectBuffer Memory</div>
                        <div class="metric-value" id="directMemory">0 MB</div>
                        <div class="metric-label" style="margin-top: 5px;">Count: <span id="directCount">0</span></div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-label">BufferPool Size</div>
                        <div class="metric-value" id="poolSize">0</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-label">Leaked Sessions (Sentinels)</div>
                        <div class="metric-value" id="leakedCount" style="color: #e74c3c;">0</div>
                    </div>
                </div>
                
                <div class="chart-container">
                    <h2 style="font-size: 16px; margin-bottom: 10px;">Heap Memory Breakdown</h2>
                    <canvas id="heapChart"></canvas>
                </div>
                
                <div>
                    <button onclick="dumpHeap()">Trigger Heap Dump</button>
                    <button onclick="forceGC()">Force GC</button>
                    <button onclick="location.reload()">Refresh</button>
                </div>
            </div>
            
            <script>
                let heapData = {
                    labels: [],
                    eden: [],
                    oldGen: [],
                    direct: []
                };
                
                function updateMetrics() {
                    document.getElementById('status').textContent = 'Updating...';
                    fetch('/api/metrics')
                        .then(r => {
                            if (!r.ok) {
                                throw new Error('HTTP ' + r.status);
                            }
                            return r.json();
                        })
                        .then(data => {
                            console.log('[Dashboard] Metrics updated:', data);
                            document.getElementById('status').textContent = 'Connected';
                            document.getElementById('lastUpdate').textContent = new Date().toLocaleTimeString();
                            
                            document.getElementById('sessionCount').textContent = 
                                data.sessionCount.toLocaleString();
                            document.getElementById('messageCount').textContent = 
                                data.messageCount.toLocaleString();
                            
                            const heapUsedMB = Math.round(data.heapUsed / 1024 / 1024);
                            const heapMaxMB = Math.round(data.heapMax / 1024 / 1024);
                            document.getElementById('heapUsed').textContent = 
                                heapUsedMB + ' / ' + heapMaxMB + ' MB';
                            
                            const heapPercent = (data.heapUsed / data.heapMax * 100).toFixed(1);
                            document.getElementById('heapProgress').style.width = heapPercent + '%';
                            
                            const directMB = Math.round(data.directMemoryUsed / 1024 / 1024);
                            document.getElementById('directMemory').textContent = directMB + ' MB';
                            document.getElementById('directCount').textContent = 
                                data.directMemoryCount.toLocaleString();
                            
                            document.getElementById('poolSize').textContent = 
                                data.poolSize.toLocaleString();
                            document.getElementById('leakedCount').textContent = 
                                data.leakedCount.toLocaleString();
                            
                            // Show leak warning
                            if (data.leakedCount > 100) {
                                document.getElementById('leakWarning').style.display = 'block';
                                document.getElementById('leakDetails').textContent = 
                                    data.leakedCount + ' sessions leaked';
                            } else {
                                document.getElementById('leakWarning').style.display = 'none';
                            }
                            
                            // Update chart data
                            const now = new Date().toLocaleTimeString();
                            heapData.labels.push(now);
                            heapData.eden.push(Math.round(data.edenUsed / 1024 / 1024));
                            heapData.oldGen.push(Math.round(data.oldGenUsed / 1024 / 1024));
                            heapData.direct.push(directMB);
                            
                            if (heapData.labels.length > 20) {
                                heapData.labels.shift();
                                heapData.eden.shift();
                                heapData.oldGen.shift();
                                heapData.direct.shift();
                            }
                            
                            drawChart();
                        })
                        .catch(err => {
                            console.error('Failed to fetch metrics:', err);
                            document.getElementById('status').textContent = 'Error: ' + err.message;
                            document.getElementById('sessionCount').textContent = 'Error';
                            document.getElementById('messageCount').textContent = 'Error';
                            document.getElementById('heapUsed').textContent = 'Error';
                        });
                }
                
                function drawChart() {
                    const canvas = document.getElementById('heapChart');
                    const ctx = canvas.getContext('2d');
                    const width = canvas.width = canvas.offsetWidth;
                    const height = canvas.height = 250;
                    
                    ctx.clearRect(0, 0, width, height);
                    
                    const maxVal = Math.max(...heapData.eden, ...heapData.oldGen, ...heapData.direct, 100);
                    const barWidth = width / heapData.labels.length;
                    
                    // Draw bars
                    for (let i = 0; i < heapData.labels.length; i++) {
                        const x = i * barWidth;
                        
                        // Direct (red)
                        const directHeight = (heapData.direct[i] / maxVal) * height;
                        ctx.fillStyle = '#e74c3c';
                        ctx.fillRect(x, height - directHeight, barWidth - 2, directHeight);
                        
                        // Old Gen (orange)
                        const oldGenHeight = (heapData.oldGen[i] / maxVal) * height;
                        ctx.fillStyle = '#f39c12';
                        ctx.fillRect(x, height - oldGenHeight, barWidth - 2, oldGenHeight);
                        
                        // Eden (green)
                        const edenHeight = (heapData.eden[i] / maxVal) * height;
                        ctx.fillStyle = '#00ff41';
                        ctx.fillRect(x, height - edenHeight, barWidth - 2, edenHeight);
                    }
                    
                    // Legend
                    ctx.fillStyle = '#00ff41';
                    ctx.fillRect(10, 10, 15, 15);
                    ctx.fillStyle = '#00ff41';
                    ctx.font = '12px Monaco';
                    ctx.fillText('Eden', 30, 22);
                    
                    ctx.fillStyle = '#f39c12';
                    ctx.fillRect(100, 10, 15, 15);
                    ctx.fillText('Old Gen', 120, 22);
                    
                    ctx.fillStyle = '#e74c3c';
                    ctx.fillRect(200, 10, 15, 15);
                    ctx.fillText('Direct', 220, 22);
                }
                
                function dumpHeap() {
                    if (confirm('Trigger heap dump? This may pause the JVM for several seconds.')) {
                        fetch('/api/dump-heap', { method: 'POST' })
                            .then(() => alert('Heap dump triggered. Check /tmp/heap-*.hprof'));
                    }
                }
                
                function forceGC() {
                    // This would require a backend endpoint
                    alert('GC is triggered automatically by leak detector every 10 seconds');
                }
                
                // Wait for DOM to be ready, then start updating
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        console.log('[Dashboard] DOM ready, starting metrics updates');
                        updateMetrics();
                        setInterval(updateMetrics, 2000);
                    });
                } else {
                    console.log('[Dashboard] DOM already ready, starting metrics updates');
                    updateMetrics();
                    setInterval(updateMetrics, 2000);
                }
            </script>
        </body>
        </html>
        """;
        
        byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, htmlBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(htmlBytes);
            os.flush();
        }
    }
    
    private void handleMetrics(HttpExchange exchange) throws IOException {
        MetricsCollector.HeapMetrics heap = gateway.getMetrics().getHeapMetrics();
        
        String json = String.format("""
            {
                "sessionCount": %d,
                "messageCount": %d,
                "heapUsed": %d,
                "heapMax": %d,
                "edenUsed": %d,
                "edenMax": %d,
                "oldGenUsed": %d,
                "oldGenMax": %d,
                "directMemoryUsed": %d,
                "directMemoryCount": %d,
                "poolSize": %d,
                "leakedCount": %d
            }
            """,
            gateway.getSessions().size(),
            gateway.getMetrics().getMessageCount(),
            heap.heapUsed(),
            heap.heapMax(),
            heap.edenUsed(),
            heap.edenMax(),
            heap.oldGenUsed(),
            heap.oldGenMax(),
            heap.directMemoryUsed(),
            heap.directMemoryCount(),
            BufferPool.poolSize(),
            gateway.getLeakDetector().getLeakedCount()
        );
        
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(200, jsonBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonBytes);
            os.flush();
        }
    }
    
    private void handleDumpHeap(HttpExchange exchange) throws IOException {
        // Trigger heap dump in background
        Thread.ofVirtual().start(() -> {
            try {
                com.sun.management.HotSpotDiagnosticMXBean mxBean = 
                    java.lang.management.ManagementFactory.getPlatformMXBean(
                        com.sun.management.HotSpotDiagnosticMXBean.class
                    );
                String filename = "/tmp/heap-manual-" + System.currentTimeMillis() + ".hprof";
                mxBean.dumpHeap(filename, true);
                System.out.println("[Dashboard] Heap dump saved: " + filename);
            } catch (IOException e) {
                System.err.println("[Dashboard] Heap dump failed: " + e.getMessage());
            }
        });
        
        String response = "{\"status\": \"heap dump triggered\"}";
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
            os.flush();
        }
    }
}
