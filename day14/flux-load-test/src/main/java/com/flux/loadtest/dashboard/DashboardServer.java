package com.flux.loadtest.dashboard;

import com.flux.loadtest.metrics.MetricsCollector;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP server for real-time dashboard.
 * Serves both HTML UI and JSON metrics endpoint.
 */
public class DashboardServer {
    
    private final HttpServer server;
    private final MetricsCollector metrics;
    
    public DashboardServer(int port, MetricsCollector metrics) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.metrics = metrics;
        
        server.createContext("/", exchange -> {
            String html = generateDashboardHTML();
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.createContext("/metrics", exchange -> {
            String json = generateMetricsJSON();
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
    
    public void start() {
        server.start();
    }
    
    public void stop() {
        server.stop(0);
    }
    
    public int getPort() {
        return server.getAddress().getPort();
    }
    
    private String generateMetricsJSON() {
        var snapshot = metrics.getSnapshot();
        
        return String.format("""
            {
              "totalAttempts": %d,
              "successfulConnections": %d,
              "failedConnections": %d,
              "activeConnections": %d,
              "messagesSent": %d,
              "messagesReceived": %d,
              "successRate": %.2f,
              "heapUsedMB": %d,
              "heapMaxMB": %d,
              "heapUsagePercent": %.2f,
              "activeThreads": %d,
              "elapsedSeconds": %d,
              "connectionsPerSecond": %.2f
            }
            """,
            snapshot.totalAttempts(),
            snapshot.successfulConnections(),
            snapshot.failedConnections(),
            snapshot.activeConnections(),
            snapshot.messagesSent(),
            snapshot.messagesReceived(),
            snapshot.successRate(),
            snapshot.heapUsedBytes() / 1_048_576,
            snapshot.heapMaxBytes() / 1_048_576,
            snapshot.heapUsagePercent(),
            snapshot.activeThreads(),
            snapshot.elapsedSeconds(),
            snapshot.connectionsPerSecond()
        );
    }
    
    private String generateDashboardHTML() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Flux Load Test Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            background: #0a0e27;
            color: #e0e0e0;
            padding: 20px;
        }
        .container { max-width: 1400px; margin: 0 auto; }
        h1 {
            font-size: 32px;
            margin-bottom: 30px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .card {
            background: #1a1f3a;
            border-radius: 12px;
            padding: 24px;
            border: 1px solid #2a2f4a;
        }
        .card h2 {
            font-size: 14px;
            text-transform: uppercase;
            letter-spacing: 1px;
            color: #888;
            margin-bottom: 12px;
        }
        .metric {
            font-size: 36px;
            font-weight: 700;
            color: #fff;
        }
        .metric.success { color: #10b981; }
        .metric.warning { color: #f59e0b; }
        .metric.error { color: #ef4444; }
        .chart-container {
            background: #1a1f3a;
            border-radius: 12px;
            padding: 24px;
            border: 1px solid #2a2f4a;
            height: 300px;
        }
        .progress-bar {
            width: 100%;
            height: 8px;
            background: #2a2f4a;
            border-radius: 4px;
            overflow: hidden;
            margin-top: 12px;
        }
        .progress-fill {
            height: 100%;
            background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
            transition: width 0.3s ease;
        }
        .status {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 600;
            margin-top: 8px;
        }
        .status.running { background: #10b98144; color: #10b981; }
        .status.complete { background: #667eea44; color: #667eea; }
        canvas { width: 100% !important; height: 100% !important; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 Flux Load Test Dashboard</h1>
        
        <div class="grid">
            <div class="card">
                <h2>Active Connections</h2>
                <div class="metric" id="activeConnections">0</div>
                <div class="status running">RUNNING</div>
            </div>
            
            <div class="card">
                <h2>Success Rate</h2>
                <div class="metric success" id="successRate">0%</div>
                <div class="progress-bar">
                    <div class="progress-fill" id="successBar" style="width: 0%"></div>
                </div>
            </div>
            
            <div class="card">
                <h2>Messages Sent</h2>
                <div class="metric" id="messagesSent">0</div>
            </div>
            
            <div class="card">
                <h2>Heap Usage</h2>
                <div class="metric warning" id="heapUsage">0%</div>
                <div class="progress-bar">
                    <div class="progress-fill" id="heapBar" style="width: 0%"></div>
                </div>
            </div>
            
            <div class="card">
                <h2>Active Threads</h2>
                <div class="metric" id="activeThreads">0</div>
                <small style="color: #888; margin-top: 8px; display: block;">
                    Virtual Threads use ~20 carrier threads
                </small>
            </div>
            
            <div class="card">
                <h2>Connections/sec</h2>
                <div class="metric" id="connectionsPerSec">0</div>
            </div>
        </div>
        
        <div class="chart-container">
            <canvas id="connectionChart"></canvas>
        </div>
    </div>
    
    <script>
        const canvas = document.getElementById('connectionChart');
        if (!canvas) {
            console.error('Canvas element not found!');
        }
        const ctx = canvas ? canvas.getContext('2d') : null;
        const dataPoints = [];
        const maxDataPoints = 60;
        
        // Initialize canvas size
        if (canvas && ctx) {
            canvas.width = canvas.offsetWidth;
            canvas.height = canvas.offsetHeight;
        }
        
        function updateMetrics() {
            fetch('/metrics')
                .then(res => {
                    if (!res.ok) {
                        throw new Error('HTTP error! status: ' + res.status);
                    }
                    return res.json();
                })
                .then(data => {
                    // Update all metric displays
                    const activeConnEl = document.getElementById('activeConnections');
                    const successRateEl = document.getElementById('successRate');
                    const messagesSentEl = document.getElementById('messagesSent');
                    const heapUsageEl = document.getElementById('heapUsage');
                    const activeThreadsEl = document.getElementById('activeThreads');
                    const connectionsPerSecEl = document.getElementById('connectionsPerSec');
                    const successBarEl = document.getElementById('successBar');
                    const heapBarEl = document.getElementById('heapBar');
                    
                    if (activeConnEl) activeConnEl.textContent = data.activeConnections.toLocaleString();
                    if (successRateEl) successRateEl.textContent = data.successRate.toFixed(2) + '%';
                    if (messagesSentEl) messagesSentEl.textContent = data.messagesSent.toLocaleString();
                    if (heapUsageEl) heapUsageEl.textContent = data.heapUsagePercent.toFixed(1) + '%';
                    if (activeThreadsEl) activeThreadsEl.textContent = data.activeThreads;
                    if (connectionsPerSecEl) connectionsPerSecEl.textContent = data.connectionsPerSecond.toFixed(1);
                    
                    if (successBarEl) successBarEl.style.width = Math.min(data.successRate, 100) + '%';
                    if (heapBarEl) heapBarEl.style.width = Math.min(data.heapUsagePercent, 100) + '%';
                    
                    // Update chart
                    if (dataPoints.length === 0 || dataPoints[dataPoints.length - 1] !== data.activeConnections) {
                        dataPoints.push(data.activeConnections);
                        if (dataPoints.length > maxDataPoints) dataPoints.shift();
                    }
                    
                    if (ctx && canvas) {
                        drawChart();
                    }
                })
                .catch(error => {
                    console.error('Error fetching metrics:', error);
                    // Retry after a short delay
                    setTimeout(updateMetrics, 2000);
                });
        }
        
        function drawChart() {
            if (!ctx || !canvas || dataPoints.length === 0) return;
            
            const width = canvas.width;
            const height = canvas.height;
            const maxValue = Math.max(...dataPoints, 1, 10000);
            
            ctx.clearRect(0, 0, width, height);
            
            // Grid lines
            ctx.strokeStyle = '#2a2f4a';
            ctx.lineWidth = 1;
            for (let i = 0; i <= 5; i++) {
                const y = (height / 5) * i;
                ctx.beginPath();
                ctx.moveTo(0, y);
                ctx.lineTo(width, y);
                ctx.stroke();
            }
            
            // Line chart
            if (dataPoints.length > 1) {
                ctx.strokeStyle = '#667eea';
                ctx.lineWidth = 2;
                ctx.beginPath();
                
                dataPoints.forEach((value, i) => {
                    const x = (width / Math.max(maxDataPoints - 1, 1)) * i;
                    const y = height - (value / maxValue) * height;
                    
                    if (i === 0) ctx.moveTo(x, y);
                    else ctx.lineTo(x, y);
                });
                
                ctx.stroke();
            }
            
            // Labels
            ctx.fillStyle = '#888';
            ctx.font = '12px sans-serif';
            ctx.fillText(maxValue.toLocaleString(), 5, 15);
            ctx.fillText('0', 5, height - 5);
        }
        
        // Initialize and start updating
        if (canvas && ctx) {
            // Resize canvas on window resize
            window.addEventListener('resize', () => {
                canvas.width = canvas.offsetWidth;
                canvas.height = canvas.offsetHeight;
                drawChart();
            });
            
            updateMetrics();
            setInterval(updateMetrics, 1000);
        } else {
            console.error('Failed to initialize dashboard canvas');
            // Still try to update metrics even if chart fails
            updateMetrics();
            setInterval(updateMetrics, 1000);
        }
    </script>
</body>
</html>
            """;
    }
}
