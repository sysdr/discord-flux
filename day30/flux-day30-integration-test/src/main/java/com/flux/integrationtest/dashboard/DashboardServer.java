package com.flux.integrationtest.dashboard;

import com.flux.integrationtest.gateway.FluxGateway;
import com.flux.integrationtest.client.LoadTestOrchestrator;
import com.flux.integrationtest.metrics.LatencyAggregator;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Real-time dashboard showing connection grid, latency histogram, and metrics.
 * Uses Server-Sent Events for live updates.
 */
public class DashboardServer {
    private static final int PORT = 9090;
    private final Gson gson = new Gson();
    private FluxGateway gateway;
    private LoadTestOrchestrator loadTest;
    
    public void start(FluxGateway gateway, LoadTestOrchestrator loadTest) throws IOException {
        this.gateway = gateway;
        this.loadTest = loadTest;
        
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        server.createContext("/api/metrics", exchange -> {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("gateway", gateway.getMetrics());
            metrics.put("loadTest", getLoadTestMetrics());
            
            String json = gson.toJson(metrics);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length());
            OutputStream os = exchange.getResponseBody();
            os.write(json.getBytes());
            os.close();
        });
        
        server.createContext("/dashboard", exchange -> {
            String html = getDashboardHtml();
            byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        });
        
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Location", "/dashboard");
            exchange.sendResponseHeaders(302, 0);
            exchange.close();
        });
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("[Dashboard] Started on http://localhost:" + PORT + "/dashboard");
    }
    
    private Map<String, Object> getLoadTestMetrics() {
        LatencyAggregator.Percentiles latency = loadTest.getLatencyAggregator().calculate();
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("phase", loadTest.getCurrentPhase().name());
        metrics.put("totalClients", loadTest.getClients().size());
        metrics.put("totalMessagesSent", loadTest.getTotalMessagesSent());
        metrics.put("totalSamples", loadTest.getLatencyAggregator().getTotalSamples());
        metrics.put("latencyAvgMs", latency.avgMs());
        metrics.put("latencyP50Ms", latency.p50Ms());
        metrics.put("latencyP95Ms", latency.p95Ms());
        metrics.put("latencyP99Ms", latency.p99Ms());
        metrics.put("latencyMaxMs", latency.maxMs());
        
        return metrics;
    }
    
    private String getDashboardHtml() {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">
    <meta http-equiv="Pragma" content="no-cache">
    <title>Flux Day 30 - Integration Test Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', system-ui, sans-serif;
            background: linear-gradient(135deg, #f0f4f8 0%, #e2e8f0 100%);
            color: #1e293b;
            padding: 20px;
            min-height: 100vh;
        }
        .header {
            border: 2px solid #3b82f6;
            background: #fff;
            padding: 20px;
            margin-bottom: 20px;
            text-align: center;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(59, 130, 246, 0.15);
        }
        .grid-container {
            display: grid;
            grid-template-columns: 2fr 1fr;
            gap: 20px;
            margin-bottom: 20px;
        }
        .panel {
            border: 2px solid #94a3b8;
            background: #fff;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.06);
        }
        .connection-grid {
            display: grid;
            grid-template-columns: repeat(40, 10px);
            gap: 2px;
            margin: 20px 0;
        }
        .connection-dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;
        }
        .dot-healthy { background: #22c55e; }
        .dot-slow { background: #eab308; }
        .dot-disconnected { background: #ef4444; }
        .dot-idle { background: #cbd5e1; }
        .metric { margin: 10px 0; }
        .metric-label { color: #64748b; }
        .metric-value { color: #1e293b; font-weight: bold; }
        .latency-bar {
            height: 20px;
            background: linear-gradient(90deg, #3b82f6, #60a5fa);
            margin: 5px 0;
            border-radius: 4px;
            min-width: 2px;
        }
        .chart {
            height: 200px;
            border: 1px solid #94a3b8;
            margin: 10px 0;
            position: relative;
            border-radius: 4px;
        }
        .phase {
            font-size: 24px;
            font-weight: bold;
            color: #3b82f6;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>INTEGRATION TEST DASHBOARD</h1>
        <p>1,000-User Guild Chat Storm - Real-Time Monitoring</p>
    </div>
    
    <div class="grid-container">
        <div class="panel">
            <h2>CONNECTION GRID (1000 clients)</h2>
            <div class="connection-grid" id="connectionGrid"></div>
            <div style="margin-top: 10px; color: #64748b;">
                <span style="color: #22c55e;">●</span> Healthy
                <span style="color: #eab308; margin-left: 20px;">●</span> Slow Consumer
                <span style="color: #ef4444; margin-left: 20px;">●</span> Disconnected
                <span style="color: #cbd5e1; margin-left: 20px;">●</span> Idle
            </div>
        </div>
        
        <div class="panel">
            <h2>LOAD TEST STATUS</h2>
            <div class="metric">
                <span class="metric-label">Phase:</span>
                <span class="phase" id="phase">IDLE</span>
            </div>
            <div class="metric">
                <span class="metric-label">Active Connections:</span>
                <span class="metric-value" id="activeConnections">0</span>
            </div>
            <div class="metric">
                <span class="metric-label">Messages Sent (Gateway):</span>
                <span class="metric-value" id="messagesSent">0</span>
            </div>
            <div class="metric">
                <span class="metric-label">Messages Sent (Load Test):</span>
                <span class="metric-value" id="loadTestMessagesSent">0</span>
            </div>
            <div class="metric">
                <span class="metric-label">Messages Received:</span>
                <span class="metric-value" id="messagesReceived">0</span>
            </div>
            <div class="metric">
                <span class="metric-label">Slow Consumers:</span>
                <span class="metric-value" id="slowConsumers">0</span>
            </div>
        </div>
    </div>
    
    <div class="grid-container">
        <div class="panel">
            <h2>LATENCY PERCENTILES (ms)</h2>
            <div class="metric">
                <span class="metric-label">P50 (Median):</span>
                <span class="metric-value" id="latencyP50">0.00</span> ms
                <div class="latency-bar" id="barP50"></div>
            </div>
            <div class="metric">
                <span class="metric-label">P95:</span>
                <span class="metric-value" id="latencyP95">0.00</span> ms
                <div class="latency-bar" id="barP95"></div>
            </div>
            <div class="metric">
                <span class="metric-label">P99:</span>
                <span class="metric-value" id="latencyP99">0.00</span> ms
                <div class="latency-bar" id="barP99"></div>
            </div>
        </div>
        
        <div class="panel">
            <h2>PERFORMANCE ASSESSMENT</h2>
            <div id="assessment"></div>
        </div>
    </div>
    
    <script>
        // Initialize connection grid
        const grid = document.getElementById('connectionGrid');
        for (let i = 0; i < 1000; i++) {
            const dot = document.createElement('div');
            dot.className = 'connection-dot dot-idle';
            dot.id = 'dot-' + i;
            grid.appendChild(dot);
        }
        
        function getMetricsUrl() {
            return (window.location.origin || 'http://' + window.location.host) + '/api/metrics';
        }
        
        function updateMetrics() {
            fetch(getMetricsUrl(), { cache: 'no-store' })
                .then(r => r.json())
                .then(data => {
                
                const gateway = data.gateway || {};
                const loadTest = data.loadTest || {};
                const conns = gateway.connections || [];
                
                // Update phase
                document.getElementById('phase').textContent = loadTest.phase || 'IDLE';
                
                // Update metrics
                document.getElementById('activeConnections').textContent = gateway.activeConnections ?? 0;
                document.getElementById('messagesSent').textContent = gateway.totalMessagesSent ?? 0;
                document.getElementById('loadTestMessagesSent').textContent = loadTest.totalMessagesSent ?? 0;
                document.getElementById('messagesReceived').textContent = gateway.totalMessagesReceived ?? 0;
                document.getElementById('slowConsumers').textContent = gateway.slowConsumerCount ?? 0;
                
                // Update latency (show N/A when no samples)
                const totalSamples = loadTest.totalSamples ?? 0;
                const p50 = loadTest.latencyP50Ms ?? 0;
                const p95 = loadTest.latencyP95Ms ?? 0;
                const p99 = loadTest.latencyP99Ms ?? 0;
                const hasLatencyData = totalSamples > 0;
                document.getElementById('latencyP50').textContent = hasLatencyData ? p50.toFixed(2) : '—';
                document.getElementById('latencyP95').textContent = hasLatencyData ? p95.toFixed(2) : '—';
                document.getElementById('latencyP99').textContent = hasLatencyData ? p99.toFixed(2) : '—';
                
                // Update latency bars (scale to 100ms max)
                document.getElementById('barP50').style.width = hasLatencyData ? Math.min(p50, 100) + '%' : '0%';
                document.getElementById('barP95').style.width = hasLatencyData ? Math.min(p95, 100) + '%' : '0%';
                document.getElementById('barP99').style.width = hasLatencyData ? Math.min(p99, 100) + '%' : '0%';
                
                // Reset all dots to idle, then update connected ones
                for (let i = 0; i < 1000; i++) {
                    const dot = document.getElementById('dot-' + i);
                    if (dot) dot.className = 'connection-dot dot-idle';
                }
                conns.forEach(conn => {
                    const dot = document.getElementById('dot-' + (conn.userId - 1));
                    if (dot) {
                        if (conn.health === 'HEALTHY') {
                            dot.className = 'connection-dot dot-healthy';
                        } else if (conn.health === 'SLOW_CONSUMER') {
                            dot.className = 'connection-dot dot-slow';
                        } else {
                            dot.className = 'connection-dot dot-disconnected';
                        }
                    }
                });
                
                // Update assessment
                let assessment = '';
                if (hasLatencyData && p95 > 0) {
                    if (p95 < 50) {
                        assessment += '<div style="color: #22c55e;">✅ P95 < 50ms: PASS</div>';
                    } else {
                        assessment += '<div style="color: #ef4444;">❌ P95 > 50ms: FAIL</div>';
                    }
                    if (p99 < 100) {
                        assessment += '<div style="color: #22c55e;">✅ P99 < 100ms: PASS</div>';
                    } else {
                        assessment += '<div style="color: #ef4444;">❌ P99 > 100ms: FAIL</div>';
                    }
                } else if (loadTest.phase === 'COMPLETE') {
                    assessment += '<div style="color: #64748b;">No latency data collected during test</div>';
                } else {
                    assessment += '<div style="color: #64748b;">Run load test to see performance assessment</div>';
                }
                
                document.getElementById('assessment').innerHTML = assessment;
                })
                .catch(err => console.error('Failed to fetch metrics:', err));
        }
        
        updateMetrics();
        setInterval(updateMetrics, 500);
    </script>
</body>
</html>
        """;
    }
}
