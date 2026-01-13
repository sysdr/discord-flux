package com.flux.gateway.dashboard;

import com.flux.gateway.core.EventLoop;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Minimalist HTTP server for real-time metrics visualization.
 * Serves a single-page dashboard with SSE for live updates.
 */
public class Dashboard {
    
    private final HttpServer server;
    private final EventLoop eventLoop;
    
    public Dashboard(int port, EventLoop eventLoop) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.eventLoop = eventLoop;
        
        server.createContext("/", exchange -> {
            byte[] response = getHtmlDashboard().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.createContext("/metrics", exchange -> {
            var metrics = eventLoop.getMetrics();
            String json = String.format(
                "{\"activeConnections\":%d,\"bytesRead\":%d,\"bytesWritten\":%d,\"messagesProcessed\":%d}",
                metrics.getActiveConnections(),
                metrics.getTotalBytesRead(),
                metrics.getTotalBytesWritten(),
                metrics.getMessagesProcessed()
            );
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.setExecutor(null); // Use default executor
    }
    
    public void start() {
        server.start();
        System.out.println("✓ Dashboard started on http://localhost:" + server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
    }
    
    private String getHtmlDashboard() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Flux Event Loop Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'SF Mono', 'Consolas', monospace;
            background: #0a0e27;
            color: #e0e0e0;
            padding: 20px;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
        }
        h1 {
            color: #ff6b35;
            margin-bottom: 30px;
            font-size: 28px;
            font-weight: 600;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .metric-card {
            background: linear-gradient(135deg, #1a1f3a 0%, #2d3561 100%);
            padding: 25px;
            border-radius: 12px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.3), 0 0 20px rgba(255,107,53,0.1);
            border: 1px solid rgba(255,107,53,0.2);
        }
        .metric-label {
            color: #a0a0a0;
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-bottom: 10px;
        }
        .metric-value {
            color: #4da6ff;
            font-size: 36px;
            font-weight: 700;
            text-shadow: 0 2px 4px rgba(77,166,255,0.3);
        }
        .chart-container {
            background: linear-gradient(135deg, #1a1f3a 0%, #2d3561 100%);
            padding: 25px;
            border-radius: 12px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.3);
            border: 1px solid rgba(77,166,255,0.2);
        }
        .chart-title {
            color: #4da6ff;
            font-size: 16px;
            margin-bottom: 15px;
            font-weight: 600;
        }
        canvas {
            width: 100% !important;
            height: 200px !important;
        }
        .status-indicator {
            display: inline-block;
            width: 10px;
            height: 10px;
            background: #4ade80;
            border-radius: 50%;
            margin-right: 8px;
            animation: pulse 2s infinite;
            box-shadow: 0 0 10px #4ade80;
        }
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        .footer {
            margin-top: 30px;
            text-align: center;
            color: #666;
            font-size: 12px;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1><span class="status-indicator"></span>Flux Event Loop - Real-Time Metrics</h1>
        
        <div class="metrics-grid">
            <div class="metric-card">
                <div class="metric-label">Active Connections</div>
                <div class="metric-value" id="activeConnections">0</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Total Bytes Read</div>
                <div class="metric-value" id="bytesRead">0</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Total Bytes Written</div>
                <div class="metric-value" id="bytesWritten">0</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Messages Processed</div>
                <div class="metric-value" id="messagesProcessed">0</div>
            </div>
        </div>
        
        <div class="chart-container">
            <div class="chart-title">Connection Timeline</div>
            <canvas id="connectionChart"></canvas>
        </div>
        
        <div class="footer">
            Flux Gateway · Day 3: The Event Loop · Refresh Rate: 500ms
        </div>
    </div>
    
    <script>
        const ctx = document.getElementById('connectionChart').getContext('2d');
        const maxDataPoints = 60;
        const chartData = {
            labels: [],
            connections: []
        };
        
        function formatBytes(bytes) {
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
            return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
        }
        
        function formatNumber(num) {
            return num.toLocaleString();
        }
        
        function drawChart() {
            const width = ctx.canvas.width;
            const height = ctx.canvas.height;
            
            ctx.clearRect(0, 0, width, height);
            
            if (chartData.connections.length === 0) return;
            
            const maxValue = Math.max(...chartData.connections, 1);
            const padding = 40;
            const chartWidth = width - 2 * padding;
            const chartHeight = height - 2 * padding;
            const pointSpacing = chartWidth / (maxDataPoints - 1);
            
            // Draw grid
            ctx.strokeStyle = 'rgba(77, 166, 255, 0.1)';
            ctx.lineWidth = 1;
            for (let i = 0; i <= 5; i++) {
                const y = padding + (chartHeight / 5) * i;
                ctx.beginPath();
                ctx.moveTo(padding, y);
                ctx.lineTo(width - padding, y);
                ctx.stroke();
            }
            
            // Draw line
            ctx.strokeStyle = '#4da6ff';
            ctx.lineWidth = 2;
            ctx.beginPath();
            chartData.connections.forEach((value, index) => {
                const x = padding + index * pointSpacing;
                const y = padding + chartHeight - (value / maxValue) * chartHeight;
                if (index === 0) {
                    ctx.moveTo(x, y);
                } else {
                    ctx.lineTo(x, y);
                }
            });
            ctx.stroke();
            
            // Draw points
            ctx.fillStyle = '#ff6b35';
            chartData.connections.forEach((value, index) => {
                const x = padding + index * pointSpacing;
                const y = padding + chartHeight - (value / maxValue) * chartHeight;
                ctx.beginPath();
                ctx.arc(x, y, 3, 0, Math.PI * 2);
                ctx.fill();
            });
            
            // Draw axes labels
            ctx.fillStyle = '#a0a0a0';
            ctx.font = '12px SF Mono, Consolas, monospace';
            ctx.fillText('0', 10, height - padding + 15);
            ctx.fillText(maxValue.toString(), 10, padding + 5);
        }
        
        async function updateMetrics() {
            try {
                const response = await fetch('/metrics');
                const data = await response.json();
                
                document.getElementById('activeConnections').textContent = formatNumber(data.activeConnections);
                document.getElementById('bytesRead').textContent = formatBytes(data.bytesRead);
                document.getElementById('bytesWritten').textContent = formatBytes(data.bytesWritten);
                document.getElementById('messagesProcessed').textContent = formatNumber(data.messagesProcessed);
                
                // Update chart data
                const now = new Date().toLocaleTimeString();
                chartData.labels.push(now);
                chartData.connections.push(data.activeConnections);
                
                if (chartData.labels.length > maxDataPoints) {
                    chartData.labels.shift();
                    chartData.connections.shift();
                }
                
                drawChart();
                
            } catch (error) {
                console.error('Failed to fetch metrics:', error);
            }
        }
        
        // Initial canvas setup
        function resizeCanvas() {
            const canvas = document.getElementById('connectionChart');
            const container = canvas.parentElement;
            canvas.width = container.clientWidth - 50;
            canvas.height = 200;
            drawChart();
        }
        
        window.addEventListener('resize', resizeCanvas);
        resizeCanvas();
        
        // Start polling
        setInterval(updateMetrics, 500);
        updateMetrics();
    </script>
</body>
</html>
""";
    }
}
