package com.flux.server;

import com.flux.partition.BucketStrategy;
import com.flux.partition.PartitionKey;
import com.flux.simulator.PartitionSimulator;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Embedded HTTP server serving the real-time partition visualization dashboard.
 */
public class DashboardServer {
    private final HttpServer server;
    private final PartitionSimulator simulator;
    private volatile PartitionSimulator.SimulationResult lastResult;

    public DashboardServer(int port, long workerId) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.simulator = new PartitionSimulator(workerId);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        setupEndpoints();
    }

    private void setupEndpoints() {
        // Main dashboard HTML
        server.createContext("/", exchange -> {
            String html = getDashboardHTML();
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        // API: Run simulation
        server.createContext("/api/simulate", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            
            BucketStrategy strategy = BucketStrategy.valueOf(params.getOrDefault("strategy", "NAIVE"));
            int messagesPerSecond = Integer.parseInt(params.getOrDefault("rate", "100"));
            int durationSeconds = Integer.parseInt(params.getOrDefault("duration", "10"));

            simulator.reset();
            lastResult = simulator.simulateWrites(
                    12345L, // Fixed channel ID
                    messagesPerSecond,
                    Duration.ofSeconds(durationSeconds),
                    strategy
            );

            String json = resultToJson(lastResult);
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        // API: Get current stats
        server.createContext("/api/stats", exchange -> {
            String json = lastResult != null ? resultToJson(lastResult) : "{}";
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        return java.util.Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(parts -> parts.length >= 1)
                .collect(Collectors.toMap(
                        parts -> parts[0],
                        parts -> parts.length > 1 ? parts[1] : "",
                        (a, b) -> a
                ));
    }

    private String resultToJson(PartitionSimulator.SimulationResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"strategy\":\"").append(result.strategy()).append("\",");
        json.append("\"totalMessages\":").append(result.totalMessages()).append(",");
        json.append("\"partitionCount\":").append(result.partitionCount()).append(",");
        json.append("\"durationSeconds\":").append(result.durationSeconds()).append(",");
        json.append("\"throughput\":").append(result.throughputPerSecond()).append(",");
        json.append("\"maxPartitionSize\":").append(result.maxPartitionSize()).append(",");
        json.append("\"avgPartitionSize\":").append(result.avgPartitionSize()).append(",");
        json.append("\"partitions\":[");

        String partitionsJson = result.partitionStats().entrySet().stream()
                .map(e -> "{\"key\":\"%s\",\"messages\":%d,\"sizeMB\":%.2f}"
                        .formatted(
                                e.getKey().toString(),
                                e.getValue().getMessageCount(),
                                e.getValue().getSizeMB()
                        ))
                .collect(Collectors.joining(","));

        json.append(partitionsJson);
        json.append("]}");
        return json.toString();
    }

    private String getDashboardHTML() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Flux - Hot Partition Simulator</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', system-ui, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        .container {
            max-width: 1400px;
            margin: 0 auto;
            background: white;
            border-radius: 16px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            overflow: hidden;
        }
        header {
            background: linear-gradient(135deg, #2d3748 0%, #1a202c 100%);
            color: white;
            padding: 30px;
            border-bottom: 4px solid #667eea;
        }
        h1 { font-size: 2.5rem; margin-bottom: 10px; }
        .subtitle { color: #a0aec0; font-size: 1.1rem; }
        .controls {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            padding: 30px;
            background: #f7fafc;
            border-bottom: 1px solid #e2e8f0;
        }
        .control-group {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }
        label {
            font-weight: 600;
            color: #2d3748;
            font-size: 0.9rem;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        select, input {
            padding: 12px;
            border: 2px solid #e2e8f0;
            border-radius: 8px;
            font-size: 1rem;
            transition: all 0.3s;
        }
        select:focus, input:focus {
            outline: none;
            border-color: #667eea;
            box-shadow: 0 0 0 3px rgba(102,126,234,0.1);
        }
        button {
            padding: 14px 28px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 8px;
            font-size: 1rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.3s;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        button:hover {
            transform: translateY(-2px);
            box-shadow: 0 10px 20px rgba(102,126,234,0.3);
        }
        button:active { transform: translateY(0); }
        button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
        .dashboard {
            padding: 30px;
        }
        .metrics {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .metric {
            background: linear-gradient(135deg, #f7fafc 0%, #edf2f7 100%);
            padding: 20px;
            border-radius: 12px;
            border: 2px solid #e2e8f0;
        }
        .metric-label {
            color: #718096;
            font-size: 0.85rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin-bottom: 8px;
        }
        .metric-value {
            color: #2d3748;
            font-size: 2rem;
            font-weight: 700;
        }
        .metric-unit {
            color: #a0aec0;
            font-size: 0.9rem;
            margin-left: 4px;
        }
        .chart-container {
            background: white;
            border: 2px solid #e2e8f0;
            border-radius: 12px;
            padding: 20px;
            margin-top: 20px;
        }
        .chart-title {
            font-size: 1.3rem;
            font-weight: 700;
            color: #2d3748;
            margin-bottom: 20px;
        }
        .bar-chart {
            display: flex;
            flex-direction: column;
            gap: 12px;
        }
        .bar-row {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .bar-label {
            min-width: 180px;
            font-size: 0.9rem;
            font-family: 'Courier New', monospace;
            color: #4a5568;
            font-weight: 600;
        }
        .bar-container {
            flex: 1;
            height: 40px;
            background: #edf2f7;
            border-radius: 8px;
            overflow: hidden;
            position: relative;
        }
        .bar-fill {
            height: 100%;
            background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
            transition: width 1s ease-out;
            display: flex;
            align-items: center;
            justify-content: flex-end;
            padding-right: 12px;
            color: white;
            font-weight: 700;
            font-size: 0.85rem;
        }
        .bar-value {
            min-width: 100px;
            text-align: right;
            font-weight: 600;
            color: #2d3748;
            font-size: 0.9rem;
        }
        .warning {
            background: #fff5f5;
            border: 2px solid #fc8181;
            border-radius: 8px;
            padding: 16px;
            margin-top: 20px;
            color: #c53030;
            font-weight: 600;
        }
        .success {
            background: #f0fff4;
            border: 2px solid #68d391;
            border-radius: 8px;
            padding: 16px;
            margin-top: 20px;
            color: #2f855a;
            font-weight: 600;
        }
        .loading {
            text-align: center;
            padding: 40px;
            color: #718096;
            font-size: 1.1rem;
        }
        .spinner {
            border: 4px solid #edf2f7;
            border-top: 4px solid #667eea;
            border-radius: 50%;
            width: 40px;
            height: 40px;
            animation: spin 1s linear infinite;
            margin: 0 auto 16px;
        }
        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>🔥 Flux Hot Partition Simulator</h1>
            <p class="subtitle">Day 36: Understanding Partition Distribution in Wide-Column Stores</p>
        </header>
        
        <div class="controls">
            <div class="control-group">
                <label for="strategy">Bucketing Strategy</label>
                <select id="strategy">
                    <option value="NAIVE">Naive (Channel ID Only)</option>
                    <option value="HOURLY">Time-Bucketed (Hourly)</option>
                    <option value="DAILY" selected>Time-Bucketed (Daily)</option>
                    <option value="WEEKLY">Time-Bucketed (Weekly)</option>
                </select>
            </div>
            
            <div class="control-group">
                <label for="rate">Messages per Second</label>
                <input type="number" id="rate" value="100" min="10" max="1000" step="10">
            </div>
            
            <div class="control-group">
                <label for="duration">Duration (seconds)</label>
                <input type="number" id="duration" value="10" min="5" max="60" step="5">
            </div>
            
            <div class="control-group">
                <label>&nbsp;</label>
                <button id="runBtn" onclick="runSimulation()">Run Simulation</button>
            </div>
        </div>
        
        <div class="dashboard">
            <div id="loading" class="loading" style="display: none;">
                <div class="spinner"></div>
                Simulating write workload...
            </div>
            
            <div id="results" style="display: none;">
                <div class="metrics">
                    <div class="metric">
                        <div class="metric-label">Total Messages</div>
                        <div class="metric-value" id="totalMessages">0</div>
                    </div>
                    <div class="metric">
                        <div class="metric-label">Partitions Created</div>
                        <div class="metric-value" id="partitionCount">0</div>
                    </div>
                    <div class="metric">
                        <div class="metric-label">Throughput</div>
                        <div class="metric-value">
                            <span id="throughput">0</span>
                            <span class="metric-unit">msgs/s</span>
                        </div>
                    </div>
                    <div class="metric">
                        <div class="metric-label">Max Partition</div>
                        <div class="metric-value">
                            <span id="maxPartition">0</span>
                            <span class="metric-unit">msgs</span>
                        </div>
                    </div>
                    <div class="metric">
                        <div class="metric-label">Avg Partition</div>
                        <div class="metric-value">
                            <span id="avgPartition">0</span>
                            <span class="metric-unit">msgs</span>
                        </div>
                    </div>
                    <div class="metric">
                        <div class="metric-label">Duration</div>
                        <div class="metric-value">
                            <span id="durationSec">0</span>
                            <span class="metric-unit">sec</span>
                        </div>
                    </div>
                </div>
                
                <div id="alert"></div>
                
                <div class="chart-container">
                    <div class="chart-title">Partition Distribution</div>
                    <div id="partitionChart" class="bar-chart"></div>
                </div>
            </div>
        </div>
    </div>
    
    <script>
        // Auto-run quick demo on page load so metrics are never zero
        document.addEventListener('DOMContentLoaded', () => {
            runQuickDemo();
        });

        async function runQuickDemo() {
            const runBtn = document.getElementById('runBtn');
            runBtn.disabled = true;
            document.getElementById('loading').style.display = 'block';
            document.getElementById('results').style.display = 'none';
            try {
                const response = await fetch('/api/simulate?strategy=DAILY&rate=50&duration=5', { method: 'POST' });
                const data = await response.json();
                displayResults(data);
            } catch (e) {
                console.error('Initial demo failed:', e);
            } finally {
                runBtn.disabled = false;
                document.getElementById('loading').style.display = 'none';
            }
        }

        async function runSimulation() {
            const strategy = document.getElementById('strategy').value;
            const rate = document.getElementById('rate').value;
            const duration = document.getElementById('duration').value;
            const runBtn = document.getElementById('runBtn');
            
            runBtn.disabled = true;
            document.getElementById('loading').style.display = 'block';
            document.getElementById('results').style.display = 'none';
            
            try {
                const response = await fetch(`/api/simulate?strategy=${strategy}&rate=${rate}&duration=${duration}`, {
                    method: 'POST'
                });
                const data = await response.json();
                displayResults(data);
            } catch (error) {
                console.error('Simulation failed:', error);
                alert('Simulation failed. Check console for details.');
            } finally {
                runBtn.disabled = false;
                document.getElementById('loading').style.display = 'none';
            }
        }
        
        function displayResults(data) {
            if (!data || typeof data.totalMessages === 'undefined') return;
            document.getElementById('results').style.display = 'block';
            document.getElementById('totalMessages').textContent = (data.totalMessages || 0).toLocaleString();
            document.getElementById('partitionCount').textContent = data.partitionCount || 0;
            document.getElementById('throughput').textContent = Math.round(data.throughput || 0).toLocaleString();
            document.getElementById('maxPartition').textContent = (data.maxPartitionSize || 0).toLocaleString();
            document.getElementById('avgPartition').textContent = Math.round(data.avgPartitionSize || 0).toLocaleString();
            document.getElementById('durationSec').textContent = (data.durationSeconds || 0).toFixed(1);
            
            // Alert based on partition size
            const alertDiv = document.getElementById('alert');
            if ((data.strategy || '') === 'NAIVE' && (data.maxPartitionSize || 0) > 500) {
                alertDiv.className = 'warning';
                alertDiv.textContent = '⚠️ WARNING: Hot partition detected! This single partition would cause severe performance degradation in production.';
            } else if (data.partitionCount > 1) {
                alertDiv.className = 'success';
                alertDiv.textContent = '✅ SUCCESS: Load distributed across ' + data.partitionCount + ' partitions. Each partition remains manageable.';
            } else {
                alertDiv.textContent = '';
            }
            
            // Render bar chart
            const chartDiv = document.getElementById('partitionChart');
            chartDiv.innerHTML = '';
            const partitions = data.partitions || [];
            const maxSize = partitions.length > 0 ? Math.max(...partitions.map(p => p.messages), 1) : 1;
            
            partitions
                .sort((a, b) => b.messages - a.messages)
                .forEach(partition => {
                    const row = document.createElement('div');
                    row.className = 'bar-row';
                    
                    const label = document.createElement('div');
                    label.className = 'bar-label';
                    label.textContent = partition.key;
                    
                    const container = document.createElement('div');
                    container.className = 'bar-container';
                    
                    const fill = document.createElement('div');
                    fill.className = 'bar-fill';
                    fill.style.width = '0%';
                    fill.textContent = partition.messages.toLocaleString();
                    setTimeout(() => {
                        fill.style.width = ((partition.messages / maxSize) * 100) + '%';
                    }, 100);
                    
                    const value = document.createElement('div');
                    value.className = 'bar-value';
                    value.textContent = partition.sizeMB.toFixed(2) + ' MB';
                    
                    container.appendChild(fill);
                    row.appendChild(label);
                    row.appendChild(container);
                    row.appendChild(value);
                    chartDiv.appendChild(row);
                });
        }
    </script>
</body>
</html>
                """;
    }

    public void start() {
        server.start();
        System.out.println("✅ Dashboard server started at http://localhost:" + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }

    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
            long workerId = args.length > 1 ? Long.parseLong(args[1]) : 1;
            
            DashboardServer server = new DashboardServer(port, workerId);
            server.start();
            
            System.out.println("Press CTRL+C to stop the server");
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
