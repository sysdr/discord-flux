package com.flux.gateway;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class DashboardServer {
    private static final int PORT = Integer.parseInt(
        System.getProperty("dashboard.port", System.getenv().getOrDefault("DASHBOARD_PORT", "8081"))
    );
    private final GatewayServer gateway;
    
    public DashboardServer(GatewayServer gateway) {
        this.gateway = gateway;
    }
    
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        server.createContext("/", exchange -> {
            if ("/".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().add("Location", "/dashboard");
                exchange.sendResponseHeaders(302, 0);
                exchange.close();
            } else {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
            }
        });
        server.createContext("/dashboard", this::handleDashboard);
        server.createContext("/api/metrics", this::handleMetrics);
        server.createContext("/api/run-tests", this::handleRunTests);
        server.createContext("/api/generate-sample-data", this::handleGenerateSampleData);
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("Dashboard available at: http://localhost:" + PORT + "/dashboard");
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        // Handle CORS if needed
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        
        String html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Flux Gateway - Resume Dashboard</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: 'Segoe UI', system-ui, sans-serif;
                    background: linear-gradient(135deg, #f0f9ff 0%, #fef3e2 100%);
                    padding: 20px;
                    color: #333;
                }
                .container {
                    max-width: 1400px;
                    margin: 0 auto;
                }
                h1 {
                    color: #2c3e50;
                    margin-bottom: 30px;
                    font-size: 2.5em;
                    text-shadow: 1px 1px 2px rgba(0,0,0,0.1);
                }
                .metrics-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                    gap: 20px;
                    margin-bottom: 30px;
                }
                .metric-card {
                    background: white;
                    border-radius: 12px;
                    padding: 25px;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                    transition: transform 0.3s ease;
                }
                .metric-card:hover {
                    transform: translateY(-5px);
                }
                .metric-label {
                    font-size: 0.9em;
                    color: #666;
                    text-transform: uppercase;
                    letter-spacing: 1px;
                    margin-bottom: 10px;
                }
                .metric-value {
                    font-size: 2.5em;
                    font-weight: bold;
                    color: #29b6f6;
                }
                .metric-unit {
                    font-size: 0.5em;
                    color: #999;
                }
                .chart-card {
                    background: white;
                    border-radius: 12px;
                    padding: 25px;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                    margin-bottom: 30px;
                }
                .controls {
                    background: white;
                    border-radius: 12px;
                    padding: 25px;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                }
                button {
                    background: linear-gradient(135deg, #4fc3f7 0%, #29b6f6 100%);
                    color: white;
                    border: none;
                    padding: 15px 30px;
                    border-radius: 8px;
                    font-size: 1em;
                    cursor: pointer;
                    margin-right: 10px;
                    transition: all 0.3s ease;
                    box-shadow: 0 4px 15px rgba(79, 195, 247, 0.4);
                }
                button:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 6px 20px rgba(79, 195, 247, 0.6);
                }
                .histogram {
                    display: flex;
                    align-items: flex-end;
                    height: 200px;
                    gap: 10px;
                    margin-top: 20px;
                }
                .bar {
                    flex: 1;
                    background: linear-gradient(to top, #4fc3f7, #29b6f6);
                    border-radius: 4px 4px 0 0;
                    position: relative;
                    min-height: 5px;
                }
                .bar-label {
                    position: absolute;
                    bottom: -25px;
                    left: 0;
                    right: 0;
                    text-align: center;
                    font-size: 0.7em;
                    color: #666;
                }
                .success { color: #10b981; }
                .warning { color: #f59e0b; }
                .error { color: #ef4444; }
                .updating {
                    animation: pulse 0.5s ease-in-out;
                }
                @keyframes pulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.7; }
                }
                .status-indicator {
                    position: fixed;
                    bottom: 10px;
                    right: 10px;
                    background: rgba(0,0,0,0.8);
                    color: white;
                    padding: 10px 15px;
                    border-radius: 6px;
                    font-size: 0.85em;
                    z-index: 1000;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                }
                .status-indicator.connected {
                    border-left: 4px solid #10b981;
                }
                .status-indicator.error {
                    border-left: 4px solid #ef4444;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>🚀 Flux Gateway - Resume Capability Dashboard</h1>
                
                <div class="metrics-grid">
                    <div class="metric-card">
                        <div class="metric-label">Active Sessions</div>
                        <div class="metric-value" id="activeSessions">0</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-label">Disconnected (Resumable)</div>
                        <div class="metric-value" id="disconnectedSessions">0</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-label">Resume Success Rate</div>
                        <div class="metric-value" id="successRate">0<span class="metric-unit">%</span></div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-label">Avg Resume Latency</div>
                        <div class="metric-value" id="avgLatency">0<span class="metric-unit">ms</span></div>
                    </div>
                </div>
                
                <div class="chart-card">
                    <h2>Resume Latency Distribution</h2>
                    <div class="histogram" id="histogram"></div>
                </div>
                
                <div class="controls">
                    <h2 style="margin-bottom: 15px;">Test Scenarios</h2>
                    <button onclick="generateSampleData()" id="generateSampleBtn">
                        📊 Generate Sample Data
                    </button>
                    <button onclick="runTests()" id="runTestsBtn">
                        🧪 Run Unit Tests
                    </button>
                    <button onclick="alert('Run ./demo.sh in terminal to simulate network partition')">
                        📡 Simulate Network Partition
                    </button>
                    <button onclick="alert('Use wscat to connect: wscat -c ws://localhost:8080')">
                        🔌 Manual WebSocket Test
                    </button>
                    <div id="testResults" style="margin-top: 20px; display: none;">
                        <h3>Test Results</h3>
                        <div id="testOutput" style="background: #f8f9fa; padding: 15px; border-radius: 8px; font-family: monospace; white-space: pre-wrap; max-height: 300px; overflow-y: auto; color: #333;"></div>
                    </div>
                </div>
            </div>
            
            <script>
                const bucketLabels = ['<1ms', '<5ms', '<10ms', '<20ms', '<50ms', '<100ms', '<200ms', '<500ms', '<1s', '<5s'];
                
                let updateInterval = null;
                let lastUpdateTime = Date.now();
                
                // Define functions globally first so they're available to onclick handlers
                window.generateSampleData = async function() {
                    const btn = document.getElementById('generateSampleBtn');
                    btn.disabled = true;
                    btn.textContent = '🔄 Generating...';
                    
                    try {
                        const response = await fetch('/api/generate-sample-data', { method: 'POST' });
                        const data = await response.json();
                        if (data.success) {
                            // Refresh metrics immediately
                            updateMetrics();
                            btn.textContent = '✅ Sample Data Generated!';
                            setTimeout(() => {
                                btn.textContent = '📊 Generate Sample Data';
                                btn.disabled = false;
                            }, 2000);
                        } else {
                            alert('Failed to generate sample data: ' + (data.error || 'Unknown error'));
                            btn.disabled = false;
                            btn.textContent = '📊 Generate Sample Data';
                        }
                    } catch (error) {
                        alert('Error generating sample data: ' + error.message);
                        btn.disabled = false;
                        btn.textContent = '📊 Generate Sample Data';
                    }
                };
                
                window.runTests = async function() {
                    const btn = document.getElementById('runTestsBtn');
                    const resultsDiv = document.getElementById('testResults');
                    const outputDiv = document.getElementById('testOutput');
                    
                    btn.disabled = true;
                    btn.textContent = '🔄 Running Tests...';
                    resultsDiv.style.display = 'block';
                    outputDiv.textContent = 'Running unit tests...\\n';
                    
                    try {
                        const response = await fetch('/api/run-tests');
                        const data = await response.json();
                        outputDiv.textContent = data.output || 'Tests completed';
                        if (data.success) {
                            outputDiv.style.color = '#10b981';
                        } else {
                            outputDiv.style.color = '#ef4444';
                        }
                    } catch (error) {
                        outputDiv.textContent = 'Error running tests: ' + error.message;
                        outputDiv.style.color = '#ef4444';
                    } finally {
                        btn.disabled = false;
                        btn.textContent = '🧪 Run Unit Tests';
                    }
                };
                
                function updateMetrics() {
                    const startTime = Date.now();
                    
                    // Add updating class to show activity
                    const cards = document.querySelectorAll('.metric-card');
                    cards.forEach(card => card.classList.add('updating'));
                    
                    fetch('/api/metrics', {
                        method: 'GET',
                        headers: {
                            'Accept': 'application/json',
                            'Cache-Control': 'no-cache'
                        }
                    })
                        .then(r => {
                            if (!r.ok) {
                                throw new Error('HTTP error! status: ' + r.status);
                            }
                            return r.json();
                        })
                        .then(data => {
                            const updateTime = Date.now() - startTime;
                            console.log('Metrics received:', data, 'Update took:', updateTime + 'ms');
                            lastUpdateTime = Date.now();
                            
                            // Remove updating class
                            cards.forEach(card => card.classList.remove('updating'));
                            
                            // Update status indicator
                            const statusIndicator = document.getElementById('statusIndicator');
                            if (statusIndicator) {
                                statusIndicator.className = 'status-indicator connected';
                                statusIndicator.innerHTML = '🟢 Connected | Last update: ' + 
                                    Math.floor((Date.now() - lastUpdateTime) / 1000) + 's ago';
                            }
                            
                            // Update active sessions
                            const activeSessions = data.activeSessions || 0;
                            const activeElem = document.getElementById('activeSessions');
                            if (activeElem.textContent !== String(activeSessions)) {
                                activeElem.textContent = activeSessions;
                            }
                            
                            // Update disconnected sessions
                            const disconnectedSessions = data.disconnectedSessions || 0;
                            const disconnectedElem = document.getElementById('disconnectedSessions');
                            if (disconnectedElem.textContent !== String(disconnectedSessions)) {
                                disconnectedElem.textContent = disconnectedSessions;
                            }
                            
                            // Update success rate
                            const successRate = parseFloat(data.successRate || 0);
                            const successElem = document.getElementById('successRate');
                            const successText = successRate.toFixed(1);
                            if (successElem.textContent !== successText) {
                                successElem.textContent = successText;
                                successElem.className = 'metric-value ' + 
                                    (successRate >= 99 ? 'success' : successRate >= 95 ? 'warning' : 'error');
                            }
                            
                            // Update average latency
                            const avgLatency = parseFloat(data.avgLatency || 0);
                            const latencyElem = document.getElementById('avgLatency');
                            const latencyText = avgLatency.toFixed(2);
                            if (latencyElem.textContent !== latencyText) {
                                latencyElem.textContent = latencyText;
                            }
                            
                            // Update histogram
                            const histogram = document.getElementById('histogram');
                            histogram.innerHTML = '';
                            
                            if (data.histogram && Array.isArray(data.histogram) && data.histogram.length > 0) {
                                const maxCount = Math.max(...data.histogram, 1);
                                data.histogram.forEach((count, i) => {
                                    const bar = document.createElement('div');
                                    bar.className = 'bar';
                                    const height = maxCount > 0 ? (count / maxCount * 100) : 0;
                                    bar.style.height = Math.max(height, 5) + '%';
                                    
                                    const label = document.createElement('div');
                                    label.className = 'bar-label';
                                    label.textContent = bucketLabels[i] || ('Bucket ' + i);
                                    bar.appendChild(label);
                                    
                                    histogram.appendChild(bar);
                                });
                            } else {
                                // Show empty histogram message
                                const emptyMsg = document.createElement('div');
                                emptyMsg.textContent = 'No latency data yet';
                                emptyMsg.style.textAlign = 'center';
                                emptyMsg.style.color = '#999';
                                emptyMsg.style.padding = '20px';
                                histogram.appendChild(emptyMsg);
                            }
                        })
                        .catch(err => {
                            console.error('Failed to fetch metrics:', err);
                            console.error('Error details:', err.message, err.stack);
                            
                            // Remove updating class
                            cards.forEach(card => card.classList.remove('updating'));
                            
                            // Update status indicator to show error
                            const statusIndicator = document.getElementById('statusIndicator');
                            if (statusIndicator) {
                                statusIndicator.className = 'status-indicator error';
                                statusIndicator.innerHTML = '🔴 Connection Error: ' + err.message;
                            }
                        });
                }
                
                // Add status indicator
                const statusIndicator = document.createElement('div');
                statusIndicator.id = 'statusIndicator';
                statusIndicator.className = 'status-indicator connected';
                statusIndicator.innerHTML = '🟢 Connecting...';
                document.body.appendChild(statusIndicator);
                
                // Update every 2 seconds
                updateInterval = setInterval(updateMetrics, 2000);
                // Initial load immediately
                updateMetrics();
                
                // Log that script is loaded
                console.log('Dashboard script loaded, starting metrics polling every 2 seconds...');
                
                // Update status indicator every second
                setInterval(() => {
                    const elapsed = Math.floor((Date.now() - lastUpdateTime) / 1000);
                    const statusIndicator = document.getElementById('statusIndicator');
                    if (statusIndicator && statusIndicator.classList.contains('connected')) {
                        statusIndicator.innerHTML = '🟢 Connected | Last update: ' + elapsed + 's ago';
                    }
                }, 1000);
            </script>
        </body>
        </html>
        """;
        
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
    
    private void handleMetrics(HttpExchange exchange) throws IOException {
        // Handle CORS preflight
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
            return;
        }
        
        try {
            Metrics metrics = gateway.getMetrics();
            long[] histogram = metrics.getLatencyHistogram();
            
            int activeSessions = gateway.getActiveSessionCount();
            int disconnectedSessions = gateway.getDisconnectedSessionCount();
            double successRate = metrics.getResumeSuccessRate();
            double avgLatency = metrics.getAverageResumeLatency();
            
            // Build JSON response
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"activeSessions\":").append(activeSessions).append(",");
            json.append("\"disconnectedSessions\":").append(disconnectedSessions).append(",");
            json.append("\"successRate\":").append(successRate).append(",");
            json.append("\"avgLatency\":").append(avgLatency).append(",");
            json.append("\"histogram\":[");
            for (int i = 0; i < histogram.length; i++) {
                if (i > 0) json.append(",");
                json.append(histogram[i]);
            }
            json.append("]}");
            
            byte[] response = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        } catch (Exception e) {
            System.err.println("Error generating metrics response: " + e.getMessage());
            e.printStackTrace();
            String errorJson = "{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
            byte[] response = errorJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(500, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
    
    private String formatHistogram(long[] histogram) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < histogram.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(histogram[i]);
        }
        return sb.toString();
    }
    
    private void handleRunTests(HttpExchange exchange) throws IOException {
        try {
            // Get the project directory (where pom.xml is located)
            String projectDir = System.getProperty("user.dir");
            // If running from target/classes, go up to project root
            if (projectDir.contains("target")) {
                projectDir = projectDir.substring(0, projectDir.indexOf("target"));
            }
            
            ProcessBuilder pb = new ProcessBuilder("mvn", "test", "-q");
            pb.directory(new java.io.File(projectDir));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            boolean success = exitCode == 0;
            
            String result = output.toString();
            // Extract key test results if available
            if (result.contains("Tests run:")) {
                int start = result.indexOf("Tests run:");
                int end = result.indexOf("\n", start + 1);
                if (end > start) {
                    String summary = result.substring(start, end);
                    result = summary + "\n" + result;
                }
            }
            
            String json = String.format(
                "{\"success\":%s,\"output\":%s}",
                success,
                escapeJson(result)
            );
            
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        } catch (Exception e) {
            String errorJson = String.format(
                "{\"success\":false,\"output\":%s}",
                escapeJson("Error running tests: " + e.getMessage())
            );
            byte[] response = errorJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
    
    private String escapeJson(String str) {
        return "\"" + str.replace("\\", "\\\\")
                         .replace("\"", "\\\"")
                         .replace("\n", "\\n")
                         .replace("\r", "\\r")
                         .replace("\t", "\\t") + "\"";
    }
    
    private void handleGenerateSampleData(HttpExchange exchange) throws IOException {
        // Handle CORS preflight
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
            return;
        }
        
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, 0);
            exchange.close();
            return;
        }
        
        try {
            gateway.generateSampleMetrics();
            String json = "{\"success\":true}";
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        } catch (Exception e) {
            String errorJson = String.format("{\"success\":false,\"error\":%s}", escapeJson(e.getMessage()));
            byte[] response = errorJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(500, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
