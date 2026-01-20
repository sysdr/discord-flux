package com.flux.serialization.server;

import com.flux.serialization.benchmark.BenchmarkRunner;
import com.flux.serialization.metrics.Metrics;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class DashboardServer {
    private final BenchmarkRunner runner;
    private HttpServer server;
    
    public DashboardServer(BenchmarkRunner runner) {
        this.runner = runner;
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        
        server.createContext("/", ex -> {
            try {
                String response = generateHTML();
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                ex.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(responseBytes);
                    os.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
                ex.sendResponseHeaders(500, 0);
            }
        });
        
        server.createContext("/metrics", ex -> {
            try {
                String json = generateMetricsJSON();
                byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(200, jsonBytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(jsonBytes);
                    os.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
                ex.sendResponseHeaders(500, 0);
            }
        });
        
        server.createContext("/trigger", ex -> {
            try {
                new Thread(() -> runner.runBenchmark()).start();
                String response = "{\"status\":\"benchmark_triggered\"}";
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(responseBytes);
                    os.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
                ex.sendResponseHeaders(500, 0);
            }
        });
        
        server.start();
        System.out.println("📊 Dashboard running at http://localhost:8080");
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
    
    private String generateMetricsJSON() {
        StringBuilder json = new StringBuilder("{\"engines\":[");
        
        boolean first = true;
        for (Metrics m : runner.getMetrics().values()) {
            if (!first) json.append(",");
            first = false;
            
            json.append("{")
                .append("\"name\":\"").append(m.getName()).append("\",")
                .append("\"throughput\":").append(String.format("%.0f", m.getThroughput())).append(",")
                .append("\"avgLatency\":").append(String.format("%.2f", m.getAverageLatencyMicros())).append(",")
                .append("\"p99Latency\":").append(m.getP99LatencyMicros()).append(",")
                .append("\"operations\":").append(m.getTotalOperations())
                .append("}");
        }
        
        json.append("]}");
        return json.toString();
    }
    
    private String generateHTML() {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Flux Serialization Benchmark</title>
            <style>
                body {
                    font-family: 'Segoe UI', system-ui, sans-serif;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    margin: 0;
                    padding: 20px;
                    color: #fff;
                }
                .container {
                    max-width: 1400px;
                    margin: 0 auto;
                }
                h1 {
                    text-align: center;
                    font-size: 2.5em;
                    margin-bottom: 10px;
                    text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
                }
                .subtitle {
                    text-align: center;
                    opacity: 0.9;
                    margin-bottom: 30px;
                }
                .grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
                    gap: 20px;
                    margin-bottom: 20px;
                }
                .card {
                    background: rgba(255,255,255,0.1);
                    backdrop-filter: blur(10px);
                    border-radius: 15px;
                    padding: 25px;
                    box-shadow: 0 8px 32px rgba(0,0,0,0.2);
                }
                .card h2 {
                    margin-top: 0;
                    font-size: 1.5em;
                    border-bottom: 2px solid rgba(255,255,255,0.3);
                    padding-bottom: 10px;
                }
                .metric {
                    display: flex;
                    justify-content: space-between;
                    padding: 10px 0;
                    border-bottom: 1px solid rgba(255,255,255,0.1);
                }
                .metric:last-child {
                    border-bottom: none;
                }
                .metric-label {
                    font-weight: 500;
                }
                .metric-value {
                    font-weight: bold;
                    font-size: 1.2em;
                }
                .chart {
                    height: 300px;
                    background: rgba(255,255,255,0.05);
                    border-radius: 10px;
                    display: flex;
                    align-items: flex-end;
                    padding: 20px;
                    gap: 20px;
                }
                .bar-container {
                    flex: 1;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                }
                .bar {
                    width: 100%;
                    background: linear-gradient(to top, #4facfe 0%, #00f2fe 100%);
                    border-radius: 8px 8px 0 0;
                    transition: all 0.3s ease;
                }
                .bar-label {
                    margin-top: 10px;
                    font-size: 0.9em;
                    text-align: center;
                }
                .refresh-btn {
                    background: rgba(255,255,255,0.2);
                    border: 2px solid rgba(255,255,255,0.4);
                    color: white;
                    padding: 12px 30px;
                    border-radius: 25px;
                    font-size: 1em;
                    cursor: pointer;
                    transition: all 0.3s;
                    display: block;
                    margin: 20px auto;
                }
                .refresh-btn:hover {
                    background: rgba(255,255,255,0.3);
                    transform: translateY(-2px);
                }
                .status {
                    text-align: center;
                    font-size: 0.9em;
                    opacity: 0.8;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>🚀 Flux Serialization Benchmark</h1>
                <p class="subtitle">Real-time Performance Metrics</p>
                
                <div class="card">
                    <h2>📊 Throughput Comparison</h2>
                    <div class="chart" id="throughput-chart">
                        <p style="text-align: center; padding: 20px;">Loading chart data...</p>
                    </div>
                </div>
                
                <div class="grid" id="metrics-grid">
                    <div class="card">
                        <p>Loading metrics...</p>
                    </div>
                </div>
                
                <button class="refresh-btn" onclick="loadMetrics()">🔄 Refresh Metrics</button>
                <p class="status" id="status">Loading metrics...</p>
            </div>
            
            <script>
                function loadMetrics() {
                    document.getElementById('status').textContent = 'Loading metrics...';
                    fetch('/metrics')
                        .then(r => {
                            if (!r.ok) {
                                throw new Error('HTTP ' + r.status);
                            }
                            return r.json();
                        })
                        .then(data => {
                            if (!data || !data.engines || data.engines.length === 0) {
                                document.getElementById('status').textContent = 'No engine data available yet. Waiting for benchmark...';
                                return;
                            }
                            updateThroughputChart(data.engines);
                            updateMetricsGrid(data.engines);
                            document.getElementById('status').textContent = 
                                'Last updated: ' + new Date().toLocaleTimeString();
                        })
                        .catch(e => {
                            console.error('Error loading metrics:', e);
                            document.getElementById('status').textContent = 
                                'Error loading metrics: ' + e.message;
                            document.getElementById('throughput-chart').innerHTML = 
                                '<p style="text-align: center; padding: 20px;">Error loading chart data</p>';
                            document.getElementById('metrics-grid').innerHTML = 
                                '<div class="card"><p>Error loading metrics. Please refresh.</p></div>';
                        });
                }
                
                function updateThroughputChart(engines) {
                    const chart = document.getElementById('throughput-chart');
                    if (!engines || engines.length === 0) {
                        chart.innerHTML = '<p style="text-align: center; padding: 20px;">No data available</p>';
                        return;
                    }
                    
                    const throughputs = engines.map(e => e.throughput || 0);
                    const maxThroughput = Math.max(...throughputs, 1); // Use 1 as minimum to avoid division by zero
                    
                    chart.innerHTML = engines.map(e => {
                        const throughput = e.throughput || 0;
                        const height = maxThroughput > 0 ? Math.max((throughput / maxThroughput) * 100, 2) : 2; // Minimum 2% for visibility
                        const displayValue = throughput >= 1000 
                            ? (throughput / 1000).toFixed(0) + 'K' 
                            : throughput.toFixed(0);
                        return `
                            <div class="bar-container">
                                <div class="bar" style="height: ${height}%"></div>
                                <div class="bar-label">
                                    <strong>${e.name || 'Unknown'}</strong><br>
                                    ${displayValue} ops/s
                                </div>
                            </div>
                        `;
                    }).join('');
                }
                
                function updateMetricsGrid(engines) {
                    const grid = document.getElementById('metrics-grid');
                    if (!engines || engines.length === 0) {
                        grid.innerHTML = '<div class="card"><p>No engine metrics available yet.</p></div>';
                        return;
                    }
                    
                    grid.innerHTML = engines.map(e => {
                        const operations = e.operations || 0;
                        const throughput = e.throughput || 0;
                        const avgLatency = e.avgLatency || 0;
                        const p99Latency = e.p99Latency || 0;
                        
                        const opsDisplay = operations >= 1000000 
                            ? (operations / 1000000).toFixed(1) + 'M'
                            : operations >= 1000
                            ? (operations / 1000).toFixed(1) + 'K'
                            : operations.toFixed(0);
                            
                        const tputDisplay = throughput >= 1000 
                            ? (throughput / 1000).toFixed(0) + 'K'
                            : throughput.toFixed(0);
                        
                        return `
                            <div class="card">
                                <h2>${e.name || 'Unknown'} Engine</h2>
                                <div class="metric">
                                    <span class="metric-label">Total Operations</span>
                                    <span class="metric-value">${opsDisplay}</span>
                                </div>
                                <div class="metric">
                                    <span class="metric-label">Throughput</span>
                                    <span class="metric-value">${tputDisplay} ops/s</span>
                                </div>
                                <div class="metric">
                                    <span class="metric-label">Avg Latency</span>
                                    <span class="metric-value">${avgLatency.toFixed(2)} µs</span>
                                </div>
                                <div class="metric">
                                    <span class="metric-label">P99 Latency</span>
                                    <span class="metric-value">${p99Latency} µs</span>
                                </div>
                            </div>
                        `;
                    }).join('');
                }
                
                // Auto-refresh every 2 seconds
                setInterval(loadMetrics, 2000);
                // Load immediately on page load
                window.addEventListener('DOMContentLoaded', loadMetrics);
                loadMetrics();
            </script>
        </body>
        </html>
        """;
    }
}
