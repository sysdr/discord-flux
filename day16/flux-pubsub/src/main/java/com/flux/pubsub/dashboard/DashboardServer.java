package com.flux.pubsub.dashboard;

import com.flux.pubsub.metrics.MetricsCollector;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class DashboardServer {
    private static final Logger log = LoggerFactory.getLogger(DashboardServer.class);
    private final HttpServer server;
    private final MetricsCollector metrics;

    public DashboardServer(int port, MetricsCollector metrics) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.metrics = metrics;
        
        server.createContext("/dashboard.html", exchange -> {
            byte[] response = getDashboardHtml().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        server.createContext("/api/metrics", exchange -> {
            String json = metrics.snapshot().toJson();
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        log.info("Dashboard server created on port {}", port);
    }

    public void start() {
        server.start();
        log.info("✅ Dashboard started: http://localhost:{}/dashboard.html", 
                 server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        log.info("Dashboard stopped");
    }

    private String getDashboardHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Flux Gateway - Pub/Sub Metrics</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', system-ui, sans-serif;
            background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
            color: #fff;
            padding: 20px;
        }
        .container {
            max-width: 1400px;
            margin: 0 auto;
        }
        h1 {
            text-align: center;
            margin-bottom: 30px;
            font-size: 2.5em;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }
        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 20px;
        }
        .card {
            background: rgba(255,255,255,0.1);
            backdrop-filter: blur(10px);
            border-radius: 12px;
            padding: 20px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.2);
        }
        .card h2 {
            font-size: 1.2em;
            margin-bottom: 15px;
            color: #a8daff;
        }
        .metric {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 10px 0;
            border-bottom: 1px solid rgba(255,255,255,0.1);
        }
        .metric:last-child {
            border-bottom: none;
        }
        .metric-label {
            font-size: 0.9em;
            opacity: 0.8;
        }
        .metric-value {
            font-size: 1.5em;
            font-weight: bold;
            color: #4fffb0;
        }
        .chart-container {
            height: 200px;
            position: relative;
            background: rgba(0,0,0,0.2);
            border-radius: 8px;
            overflow: hidden;
        }
        .chart-bar {
            position: absolute;
            bottom: 0;
            background: linear-gradient(180deg, #4fffb0 0%, #1de9b6 100%);
            border-radius: 4px 4px 0 0;
            transition: height 0.3s ease;
        }
        .status-indicator {
            display: inline-block;
            width: 12px;
            height: 12px;
            border-radius: 50%;
            margin-right: 8px;
            animation: pulse 2s infinite;
        }
        .status-ok { background: #4fffb0; }
        .status-warn { background: #ffd54f; }
        .status-error { background: #ff5252; }
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        .legend {
            display: flex;
            gap: 20px;
            justify-content: center;
            margin-top: 10px;
            font-size: 0.9em;
        }
        .legend-item {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .legend-color {
            width: 16px;
            height: 16px;
            border-radius: 3px;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 Flux Gateway Dashboard</h1>
        
        <div class="grid">
            <!-- Publisher Metrics -->
            <div class="card">
                <h2><span class="status-indicator status-ok"></span>Publisher</h2>
                <div class="metric">
                    <span class="metric-label">Messages Published</span>
                    <span class="metric-value" id="publish-count">0</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Errors</span>
                    <span class="metric-value" id="publish-errors">0</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Avg Latency</span>
                    <span class="metric-value" id="publish-latency">0ms</span>
                </div>
            </div>

            <!-- Consumer Metrics -->
            <div class="card">
                <h2><span class="status-indicator status-ok"></span>Consumer</h2>
                <div class="metric">
                    <span class="metric-label">Messages Consumed</span>
                    <span class="metric-value" id="consume-count">0</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Errors</span>
                    <span class="metric-value" id="consume-errors">0</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Avg Batch Size</span>
                    <span class="metric-value" id="batch-size">0.0</span>
                </div>
            </div>

            <!-- Fan-Out Metrics -->
            <div class="card">
                <h2><span class="status-indicator status-ok"></span>Fan-Out</h2>
                <div class="metric">
                    <span class="metric-label">Guild 1001 Deliveries</span>
                    <span class="metric-value" id="fanout-1001">0</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Dropped (Slow Clients)</span>
                    <span class="metric-value" id="dropped-1001">0</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Drop Rate</span>
                    <span class="metric-value" id="drop-rate">0%</span>
                </div>
            </div>
        </div>

        <!-- Throughput Chart -->
        <div class="card">
            <h2>📊 Message Throughput (last 60 seconds)</h2>
            <div class="chart-container" id="throughput-chart"></div>
            <div class="legend">
                <div class="legend-item">
                    <div class="legend-color" style="background: #4fffb0;"></div>
                    <span>Published</span>
                </div>
                <div class="legend-item">
                    <div class="legend-color" style="background: #ffd54f;"></div>
                    <span>Consumed</span>
                </div>
            </div>
        </div>
    </div>

    <script>
        const throughputData = [];
        const maxDataPoints = 60;
        let lastPublishCount = 0;
        let lastConsumeCount = 0;

        async function updateMetrics() {
            try {
                const response = await fetch('/api/metrics');
                const data = await response.json();

                // Update publisher metrics
                document.getElementById('publish-count').textContent = data.publishCount.toLocaleString();
                document.getElementById('publish-errors').textContent = data.publishErrors;
                document.getElementById('publish-latency').textContent = data.avgPublishLatencyMs.toFixed(2) + 'ms';

                // Update consumer metrics
                document.getElementById('consume-count').textContent = data.consumeCount.toLocaleString();
                document.getElementById('consume-errors').textContent = data.consumeErrors;
                document.getElementById('batch-size').textContent = data.avgBatchSize.toFixed(1);

                // Update fan-out metrics
                const fanout = data.fanOutByGuild['1001'] || 0;
                const dropped = data.droppedByGuild['1001'] || 0;
                document.getElementById('fanout-1001').textContent = fanout.toLocaleString();
                document.getElementById('dropped-1001').textContent = dropped.toLocaleString();
                
                const dropRate = fanout > 0 ? ((dropped / fanout) * 100) : 0;
                document.getElementById('drop-rate').textContent = dropRate.toFixed(2) + '%';

                // Calculate incremental changes for throughput chart (messages per second)
                const publishDelta = data.publishCount - lastPublishCount;
                const consumeDelta = data.consumeCount - lastConsumeCount;
                lastPublishCount = data.publishCount;
                lastConsumeCount = data.consumeCount;

                // Update throughput chart with incremental values
                throughputData.push({
                    published: publishDelta,
                    consumed: consumeDelta,
                    timestamp: Date.now()
                });
                if (throughputData.length > maxDataPoints) {
                    throughputData.shift();
                }
                updateChart();

            } catch (error) {
                console.error('Failed to fetch metrics:', error);
            }
        }

        function updateChart() {
            const chart = document.getElementById('throughput-chart');
            chart.innerHTML = '';

            if (throughputData.length < 1) {
                // Show empty state
                const emptyMsg = document.createElement('div');
                emptyMsg.style.cssText = 'position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); color: rgba(255,255,255,0.5); font-size: 0.9em;';
                emptyMsg.textContent = 'Waiting for data...';
                chart.appendChild(emptyMsg);
                return;
            }

            // Find max value for scaling
            const maxValue = Math.max(
                1, // Ensure at least 1 to avoid division by zero
                ...throughputData.map(d => Math.max(d.published, d.consumed))
            );

            const barWidth = chart.offsetWidth / maxDataPoints;
            const chartHeight = 200;

            throughputData.forEach((data, index) => {
                const publishHeight = Math.max(1, (data.published / maxValue) * chartHeight);
                const consumeHeight = Math.max(1, (data.consumed / maxValue) * chartHeight);

                // Published bar
                if (data.published > 0) {
                    const pubBar = document.createElement('div');
                    pubBar.className = 'chart-bar';
                    pubBar.style.left = (index * barWidth) + 'px';
                    pubBar.style.width = (barWidth * 0.4) + 'px';
                    pubBar.style.height = publishHeight + 'px';
                    pubBar.style.background = 'linear-gradient(180deg, #4fffb0 0%, #1de9b6 100%)';
                    pubBar.title = `Published: ${data.published} msg/sec`;
                    chart.appendChild(pubBar);
                }

                // Consumed bar
                if (data.consumed > 0) {
                    const conBar = document.createElement('div');
                    conBar.className = 'chart-bar';
                    conBar.style.left = (index * barWidth + barWidth * 0.5) + 'px';
                    conBar.style.width = (barWidth * 0.4) + 'px';
                    conBar.style.height = consumeHeight + 'px';
                    conBar.style.background = 'linear-gradient(180deg, #ffd54f 0%, #ffb300 100%)';
                    conBar.title = `Consumed: ${data.consumed} msg/sec`;
                    chart.appendChild(conBar);
                }
            });
        }

        // Update every second
        setInterval(updateMetrics, 1000);
        updateMetrics();
    </script>
</body>
</html>
        """;
    }
}
