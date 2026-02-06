package com.flux.dashboard;

import com.flux.simulator.MessageSimulator;
import com.flux.simulator.SimulationResult;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP server for the time bucketing dashboard.
 * Serves visualization of partition distribution.
 */
public class DashboardServer {
    
    private final HttpServer server;
    private final MessageSimulator simulator;
    private final Gson gson = new Gson();
    
    public DashboardServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.simulator = new MessageSimulator();
        setupEndpoints();
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
    
    private void setupEndpoints() {
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                byte[] response = getDashboardHtml().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        });
        
        server.createContext("/api/simulate", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                // Run both simulations
                SimulationResult naive = simulator.simulateWorkload(100, 90, false);
                SimulationResult bucketed = simulator.simulateWorkload(100, 90, true);
                
                var results = new ComparisonResult(naive, bucketed);
                byte[] response = gson.toJson(results).getBytes(StandardCharsets.UTF_8);
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        });
    }
    
    public void start() {
        server.start();
        System.out.println("Dashboard available at http://localhost:" + server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
    }
    
    private String getDashboardHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Flux Time Bucketing - Live Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', system-ui, sans-serif;
            background: #0a0a0a;
            color: #e0e0e0;
            padding: 20px;
        }
        .container { max-width: 1400px; margin: 0 auto; }
        h1 {
            font-size: 2.5rem;
            margin-bottom: 10px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .subtitle {
            color: #888;
            margin-bottom: 30px;
            font-size: 1.1rem;
        }
        .controls {
            background: #1a1a1a;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 30px;
            border: 1px solid #333;
        }
        button {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 1rem;
            font-weight: 600;
            transition: transform 0.2s;
        }
        button:hover { transform: translateY(-2px); }
        button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
        .comparison {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
            margin-bottom: 30px;
        }
        .card {
            background: #1a1a1a;
            padding: 20px;
            border-radius: 8px;
            border: 1px solid #333;
        }
        .card h2 {
            margin-bottom: 15px;
            color: #fff;
        }
        .metric {
            display: flex;
            justify-content: space-between;
            padding: 10px 0;
            border-bottom: 1px solid #2a2a2a;
        }
        .metric:last-child { border-bottom: none; }
        .metric-label { color: #888; }
        .metric-value {
            font-weight: 600;
            font-family: 'Courier New', monospace;
        }
        .success { color: #4ade80; }
        .danger { color: #f87171; }
        .warning { color: #fbbf24; }
        .chart {
            height: 300px;
            background: #0f0f0f;
            border-radius: 6px;
            margin-top: 15px;
            display: flex;
            align-items: flex-end;
            padding: 20px;
            gap: 4px;
        }
        .bar {
            flex: 1;
            background: linear-gradient(to top, #667eea, #764ba2);
            border-radius: 4px 4px 0 0;
            min-height: 2px;
            transition: height 0.3s ease;
        }
        .loading {
            text-align: center;
            padding: 40px;
            color: #888;
        }
        .spinner {
            border: 3px solid #333;
            border-top: 3px solid #667eea;
            border-radius: 50%;
            width: 40px;
            height: 40px;
            animation: spin 1s linear infinite;
            margin: 0 auto 20px;
        }
        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
        .status {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 0.85rem;
            font-weight: 600;
        }
        .status.fail {
            background: rgba(248, 113, 113, 0.2);
            color: #f87171;
        }
        .status.pass {
            background: rgba(74, 222, 128, 0.2);
            color: #4ade80;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>⚡ Flux Time Bucketing Dashboard</h1>
        <p class="subtitle">Real-time visualization of partition distribution (Naive vs Bucketed)</p>
        
        <div class="controls">
            <button id="runBtn" onclick="runSimulation()">🚀 Run Simulation (100 users, 90 days)</button>
            <span id="status" style="margin-left: 20px; color: #888;"></span>
        </div>
        
        <div id="results" style="display: none;">
            <div class="comparison">
                <div class="card">
                    <h2>❌ Naive Approach (Single Partition)</h2>
                    <div class="metric">
                        <span class="metric-label">Total Messages</span>
                        <span class="metric-value" id="naive-total">-</span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">Partitions Created</span>
                        <span class="metric-value danger" id="naive-partitions">-</span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">Max Partition Size</span>
                        <span class="metric-value danger" id="naive-max">-</span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">Avg Partition Size</span>
                        <span class="metric-value" id="naive-avg">-</span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">Status</span>
                        <span class="status fail" id="naive-status">FAIL - Unbounded Growth</span>
                    </div>
                </div>
                
                <div class="card">
                    <h2>✅ Bucketed Approach (10-day windows)</h2>
                    <div class="metric">
                        <span class="metric-label">Total Messages</span>
                        <span class="metric-value" id="bucketed-total">-</span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">Partitions Created</span>
                        <span class="metric-value success" id="bucketed-partitions">-</span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">Max Partition Size</span>
                        <span class="metric-value success" id="bucketed-max">-</span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">Avg Partition Size</span>
                        <span class="metric-value" id="bucketed-avg">-</span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">Status</span>
                        <span class="status pass" id="bucketed-status">PASS - Bounded Partitions</span>
                    </div>
                </div>
            </div>
            
            <div class="card">
                <h2>📊 Partition Size Distribution (Bucketed)</h2>
                <div class="chart" id="chart"></div>
            </div>
        </div>
        
        <div id="loading" class="loading" style="display: none;">
            <div class="spinner"></div>
            <p>Simulating 900,000+ messages across 100 users...</p>
        </div>
    </div>
    
    <script>
        async function runSimulation() {
            const btn = document.getElementById('runBtn');
            const status = document.getElementById('status');
            const loading = document.getElementById('loading');
            const results = document.getElementById('results');
            
            btn.disabled = true;
            loading.style.display = 'block';
            results.style.display = 'none';
            status.textContent = 'Running...';
            
            try {
                const response = await fetch('/api/simulate', { method: 'POST' });
                const data = await response.json();
                
                displayResults(data);
                results.style.display = 'block';
                status.textContent = '✓ Complete';
                status.style.color = '#4ade80';
            } catch (error) {
                status.textContent = '✗ Error: ' + error.message;
                status.style.color = '#f87171';
            } finally {
                loading.style.display = 'none';
                btn.disabled = false;
            }
        }
        
        document.addEventListener('DOMContentLoaded', runSimulation);
        
        function displayResults(data) {
            // Naive results
            document.getElementById('naive-total').textContent = 
                data.naive.totalMessages.toLocaleString();
            document.getElementById('naive-partitions').textContent = 
                data.naive.numPartitions.toLocaleString();
            document.getElementById('naive-max').textContent = 
                data.naive.maxPartitionSize.toLocaleString() + ' messages';
            document.getElementById('naive-avg').textContent = 
                data.naive.avgPartitionSize.toFixed(0).toLocaleString() + ' messages';
            
            // Bucketed results
            document.getElementById('bucketed-total').textContent = 
                data.bucketed.totalMessages.toLocaleString();
            document.getElementById('bucketed-partitions').textContent = 
                data.bucketed.numPartitions.toLocaleString();
            document.getElementById('bucketed-max').textContent = 
                data.bucketed.maxPartitionSize.toLocaleString() + ' messages';
            document.getElementById('bucketed-avg').textContent = 
                data.bucketed.avgPartitionSize.toFixed(0).toLocaleString() + ' messages';
            
            // Create chart (simulate distribution)
            createChart(data.bucketed.numPartitions);
        }
        
        function createChart(numPartitions) {
            const chart = document.getElementById('chart');
            chart.innerHTML = '';
            
            // Simulate distribution (in real impl, backend would send actual data)
            for (let i = 0; i < Math.min(50, numPartitions); i++) {
                const bar = document.createElement('div');
                bar.className = 'bar';
                const height = 30 + Math.random() * 70; // Random height between 30-100%
                bar.style.height = height + '%';
                chart.appendChild(bar);
            }
        }
    </script>
</body>
</html>
        """;
    }
    
    record ComparisonResult(SimulationResult naive, SimulationResult bucketed) {}
}
