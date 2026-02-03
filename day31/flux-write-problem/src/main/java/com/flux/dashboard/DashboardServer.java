package com.flux.dashboard;

import com.flux.benchmark.BenchmarkRunner;
import com.flux.benchmark.MetricsCollector;
import com.flux.persistence.LSMSimulator;
import com.flux.persistence.PostgresWriter;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP server for real-time dashboard.
 */
public class DashboardServer {
    
    private final HttpServer server;
    private final BenchmarkRunner benchmarkRunner;
    private final MetricsCollector metricsCollector;
    private final Gson gson = new Gson();
    
    private PostgresWriter postgresWriter;
    private LSMSimulator lsmSimulator;
    
    public DashboardServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.benchmarkRunner = new BenchmarkRunner();
        this.metricsCollector = new MetricsCollector();
        
        setupRoutes();
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
    
    private void setupRoutes() {
        server.createContext("/", exchange -> {
            var response = getDashboardHTML();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        
        server.createContext("/api/metrics", exchange -> {
            var metrics = metricsCollector.snapshot();
            var json = gson.toJson(metrics);
            sendJSON(exchange, json);
        });
        
        server.createContext("/api/benchmark/postgres", exchange -> {
            try {
                initPostgres();
                var result = benchmarkRunner.runPostgresBenchmark(postgresWriter);
                var json = gson.toJson(result);
                sendJSON(exchange, json);
            } catch (Exception e) {
                sendJSON(exchange, "{\"error\": \"" + e.getMessage() + "\"}");
            }
        });
        
        server.createContext("/api/benchmark/lsm", exchange -> {
            try {
                initLSM();
                var result = benchmarkRunner.runLSMBenchmark(lsmSimulator);
                var json = gson.toJson(result);
                sendJSON(exchange, json);
            } catch (Exception e) {
                sendJSON(exchange, "{\"error\": \"" + e.getMessage() + "\"}");
            }
        });
    }
    
    private void initPostgres() {
        if (postgresWriter == null) {
            postgresWriter = new PostgresWriter(
                "jdbc:postgresql://localhost:5432/fluxdb",
                "postgres",
                "flux"
            );
        }
    }
    
    private void initLSM() {
        if (lsmSimulator == null) {
            lsmSimulator = new LSMSimulator();
        }
    }
    
    private void sendJSON(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    public void start() {
        server.start();
        System.out.println("Dashboard running at http://localhost:" + server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
        if (postgresWriter != null) {
            postgresWriter.close();
        }
        if (lsmSimulator != null) {
            lsmSimulator.close();
        }
    }
    
    private String getDashboardHTML() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Flux Day 31 - Write Problem Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            font-family: 'Monaco', 'Courier New', monospace; 
            background: #0a0e27; 
            color: #e0e0e0;
            padding: 20px;
        }
        .header { 
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 20px;
        }
        h1 { color: white; font-size: 24px; }
        .subtitle { color: #d0d0d0; font-size: 14px; margin-top: 5px; }
        .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
        .panel { 
            background: #1a1f3a; 
            padding: 20px; 
            border-radius: 8px;
            border: 1px solid #2a3f5f;
        }
        .panel h2 { color: #667eea; font-size: 18px; margin-bottom: 15px; }
        button {
            background: #667eea;
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 14px;
            margin: 5px;
            transition: all 0.3s;
        }
        button:hover { background: #764ba2; transform: translateY(-2px); }
        button:disabled { background: #444; cursor: not-allowed; }
        .metrics { 
            display: grid; 
            grid-template-columns: 1fr 1fr; 
            gap: 10px; 
            margin-top: 15px;
        }
        .metric {
            background: #0f1729;
            padding: 15px;
            border-radius: 6px;
            border-left: 3px solid #667eea;
        }
        .metric-label { color: #888; font-size: 12px; }
        .metric-value { color: #fff; font-size: 24px; font-weight: bold; margin-top: 5px; }
        .result {
            background: #0f1729;
            padding: 15px;
            border-radius: 6px;
            margin-top: 10px;
            white-space: pre-wrap;
            font-size: 13px;
        }
        .loading { color: #667eea; }
        .success { color: #4ade80; }
        .error { color: #f87171; }
    </style>
</head>
<body>
    <div class="header">
        <h1>⚡ The Write Problem</h1>
        <div class="subtitle">Postgres B-Trees vs LSM Tree Append-Only Writes</div>
    </div>
    
    <div class="grid">
        <div class="panel">
            <h2>🎯 Benchmarks</h2>
            <button onclick="runPostgresBenchmark()">Run Postgres Benchmark</button>
            <button onclick="runLSMBenchmark()">Run LSM Simulation</button>
            <div id="benchmark-result" class="result"></div>
        </div>
        
        <div class="panel">
            <h2>📊 JVM Metrics</h2>
            <div class="metrics">
                <div class="metric">
                    <div class="metric-label">Heap Used</div>
                    <div class="metric-value" id="heap-used">0 MB</div>
                </div>
                <div class="metric">
                    <div class="metric-label">Non-Heap Used</div>
                    <div class="metric-value" id="nonheap-used">0 MB</div>
                </div>
                <div class="metric">
                    <div class="metric-label">GC Count (Delta)</div>
                    <div class="metric-value" id="gc-count">0</div>
                </div>
                <div class="metric">
                    <div class="metric-label">GC Time (Delta)</div>
                    <div class="metric-value" id="gc-time">0 ms</div>
                </div>
            </div>
        </div>
    </div>
    
    <script>
        async function runPostgresBenchmark() {
            const resultDiv = document.getElementById('benchmark-result');
            resultDiv.className = 'result loading';
            resultDiv.textContent = 'Running Postgres benchmark...\\n(This may take 30-60 seconds)';
            
            try {
                const response = await fetch('/api/benchmark/postgres');
                const result = await response.json();
                
                if (result.error) {
                    resultDiv.className = 'result error';
                    resultDiv.textContent = 'Error: ' + result.error;
                } else {
                    resultDiv.className = 'result success';
                    resultDiv.textContent = formatBenchmarkResult(result);
                }
            } catch (error) {
                resultDiv.className = 'result error';
                resultDiv.textContent = 'Error: ' + error.message;
            }
        }
        
        async function runLSMBenchmark() {
            const resultDiv = document.getElementById('benchmark-result');
            resultDiv.className = 'result loading';
            resultDiv.textContent = 'Running LSM simulation...\\n(This may take 10-20 seconds)';
            
            try {
                const response = await fetch('/api/benchmark/lsm');
                const result = await response.json();
                
                if (result.error) {
                    resultDiv.className = 'result error';
                    resultDiv.textContent = 'Error: ' + result.error;
                } else {
                    resultDiv.className = 'result success';
                    resultDiv.textContent = formatBenchmarkResult(result);
                }
            } catch (error) {
                resultDiv.className = 'result error';
                resultDiv.textContent = 'Error: ' + error.message;
            }
        }
        
        function formatBenchmarkResult(result) {
            return `${result.name} Results:
  Messages Written: ${result.messagesWritten.toLocaleString()}
  Errors: ${result.errors}
  Duration: ${result.durationMs.toLocaleString()} ms
  Throughput: ${Math.round(result.throughputPerSec).toLocaleString()} msg/sec`;
        }
        
        async function updateMetrics() {
            try {
                const response = await fetch('/api/metrics');
                const metrics = await response.json();
                
                document.getElementById('heap-used').textContent = 
                    Math.round(metrics.heapUsed / 1024 / 1024) + ' MB';
                document.getElementById('nonheap-used').textContent = 
                    Math.round(metrics.nonHeapUsed / 1024 / 1024) + ' MB';
                document.getElementById('gc-count').textContent = metrics.gcCount;
                document.getElementById('gc-time').textContent = metrics.gcTimeMs + ' ms';
            } catch (error) {
                console.error('Metrics update failed:', error);
            }
        }
        
        // Update metrics every 2 seconds
        setInterval(updateMetrics, 2000);
        updateMetrics();
    </script>
</body>
</html>
        """;
    }
}
