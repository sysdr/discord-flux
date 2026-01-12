package com.flux.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight HTTP server for real-time metrics dashboard.
 */
public class DashboardServer {
    
    private final int port;
    private final MetricsCollector metrics;

    public DashboardServer(int port, MetricsCollector metrics) {
        this.port = port;
        this.metrics = metrics;
    }

    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            
            server.createContext("/", exchange -> {
                String response = getHtmlDashboard();
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            });

            server.createContext("/api/metrics", exchange -> {
                String json = String.format(
                    "{\"connections\":%d,\"frames\":%d,\"bytes\":%d}",
                    metrics.getActiveConnections(),
                    metrics.getTotalFrames(),
                    metrics.getBytesReceived()
                );
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(json.getBytes());
                }
            });

            server.setExecutor(null);
            server.start();
            System.out.println("📊 Dashboard server started on port " + port);
        } catch (IOException e) {
            System.err.println("Failed to start dashboard: " + e.getMessage());
        }
    }

    private String getHtmlDashboard() {
        return """
<!DOCTYPE html>
<html>
<head>
    <title>Flux Gateway Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Monaco', 'Courier New', monospace;
            background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
            color: #fff;
            padding: 20px;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
        }
        h1 {
            text-align: center;
            margin-bottom: 30px;
            font-size: 2.5em;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .metric-card {
            background: rgba(255, 255, 255, 0.1);
            backdrop-filter: blur(10px);
            border-radius: 15px;
            padding: 30px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
            border: 1px solid rgba(255, 255, 255, 0.18);
            transition: transform 0.3s ease;
        }
        .metric-card:hover {
            transform: translateY(-5px);
        }
        .metric-label {
            font-size: 0.9em;
            opacity: 0.8;
            margin-bottom: 10px;
        }
        .metric-value {
            font-size: 3em;
            font-weight: bold;
            color: #ff9500;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }
        .chart-container {
            background: rgba(255, 255, 255, 0.1);
            backdrop-filter: blur(10px);
            border-radius: 15px;
            padding: 30px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
            border: 1px solid rgba(255, 255, 255, 0.18);
            margin-bottom: 30px;
        }
        canvas {
            width: 100%;
            height: 300px;
            display: block;
        }
        .status {
            text-align: center;
            padding: 15px;
            background: rgba(76, 175, 80, 0.3);
            border-radius: 10px;
            font-size: 1.2em;
            margin-bottom: 20px;
        }
        .footer {
            text-align: center;
            opacity: 0.7;
            margin-top: 40px;
            font-size: 0.9em;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>⚡ Flux Gateway Monitor</h1>
        <div class="status">🟢 Server Running - Real-time Frame Parser</div>
        
        <div class="metrics-grid">
            <div class="metric-card">
                <div class="metric-label">Active Connections</div>
                <div class="metric-value" id="connections">0</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Total Frames Parsed</div>
                <div class="metric-value" id="frames">0</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Bytes Received</div>
                <div class="metric-value" id="bytes">0</div>
            </div>
        </div>

        <div class="chart-container">
            <h2 style="margin-bottom: 20px;">📈 Frame Throughput</h2>
            <canvas id="chart" width="1140" height="300"></canvas>
        </div>

        <div class="footer">
            Flux Day 2: WebSocket Frame Parser | Zero-Copy • Virtual Threads • Production Grade
        </div>
    </div>

    <script>
        let canvas, ctx;
        const maxDataPoints = 60;
        const frameData = [];
        let lastFrameCount = 0;

        function initCanvas() {
            canvas = document.getElementById('chart');
            if (!canvas) {
                console.error('Canvas element not found');
                return false;
            }
            ctx = canvas.getContext('2d');
            if (!ctx) {
                console.error('Could not get canvas context');
                return false;
            }
            // Set canvas size properly - use container width or default
            const container = canvas.parentElement;
            const containerWidth = container ? container.clientWidth - 60 : 1140;
            canvas.width = containerWidth;
            canvas.height = 300;
            return true;
        }

        function updateMetrics() {
            if (!ctx || !canvas) {
                if (!initCanvas()) return;
            }
            fetch('/api/metrics')
                .then(r => r.json())
                .then(data => {
                    document.getElementById('connections').textContent = data.connections;
                    document.getElementById('frames').textContent = data.frames.toLocaleString();
                    document.getElementById('bytes').textContent = (data.bytes / 1024).toFixed(2) + ' KB';

                    // Calculate frame rate
                    const currentFrames = data.frames;
                    const frameRate = currentFrames - lastFrameCount;
                    lastFrameCount = currentFrames;

                    frameData.push(frameRate);
                    if (frameData.length > maxDataPoints) {
                        frameData.shift();
                    }

                    drawChart();
                })
                .catch(e => console.error('Metrics fetch error:', e));
        }

        function drawChart() {
            if (!ctx || !canvas) return;
            const width = canvas.width;
            const height = canvas.height;
            const max = Math.max(...frameData.length > 0 ? frameData : [0], 10);

            ctx.clearRect(0, 0, width, height);

            // Draw grid
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
            ctx.lineWidth = 1;
            for (let i = 0; i < 5; i++) {
                const y = (height / 4) * i;
                ctx.beginPath();
                ctx.moveTo(0, y);
                ctx.lineTo(width, y);
                ctx.stroke();
            }

            // Draw line
            if (frameData.length > 1) {
                ctx.strokeStyle = '#ff9500';
                ctx.lineWidth = 3;
                ctx.lineCap = 'round';
                ctx.lineJoin = 'round';

                ctx.beginPath();
                frameData.forEach((value, i) => {
                    const x = (i / maxDataPoints) * width;
                    const y = height - (value / max) * height;
                    if (i === 0) {
                        ctx.moveTo(x, y);
                    } else {
                        ctx.lineTo(x, y);
                    }
                });
                ctx.stroke();

                // Draw area fill
                ctx.lineTo(width, height);
                ctx.lineTo(0, height);
                ctx.closePath();
                ctx.fillStyle = 'rgba(255, 149, 0, 0.2)';
                ctx.fill();
            }

            // Draw labels
            ctx.fillStyle = '#fff';
            ctx.font = '12px Monaco';
            ctx.fillText(max + ' frames/sec', 10, 20);
            ctx.fillText('0', 10, height - 10);
        }

        // Initialize and start updates
        function start() {
            if (initCanvas()) {
                setInterval(updateMetrics, 1000);
                updateMetrics();
            } else {
                console.error('Failed to initialize dashboard');
            }
        }

        // Wait for DOM to be ready
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', start);
        } else {
            start();
        }
    </script>
</body>
</html>
""";
    }
}
