package com.flux.gateway.server;

import com.flux.gateway.router.IntentAwareRouter;
import com.flux.gateway.connection.GatewayConnection;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class DashboardServer {
    private final IntentAwareRouter router;
    private final HttpServer server;

    public DashboardServer(IntentAwareRouter router, int port) throws IOException {
        this.router = router;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/dashboard", exchange -> {
            String html = generateDashboardHtml();
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.createContext("/metrics", exchange -> {
            var metrics = router.getMetrics();
            byte[] body = metrics.toJson().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.createContext("/connections", exchange -> {
            var sb = new StringBuilder("[");
            boolean first = true;
            var ids = new java.util.TreeSet<>(router.getConnectionIds());
            for (var userId : ids) {
                var conn = router.getConnection(userId);
                if (conn != null) {
                    if (!first) sb.append(",");
                    sb.append(String.format(
                        "{\"userId\":\"%s\",\"intents\":%d,\"sent\":%d,\"filtered\":%d}",
                        conn.getUserId(), conn.getIntents(), 
                        conn.getEventsSent(), conn.getEventsFiltered()
                    ));
                    first = false;
                }
            }
            sb.append("]");
            
            byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    public void start() {
        server.start();
        System.out.println("Dashboard server started on http://localhost:" + 
                         server.getAddress().getPort() + "/dashboard");
    }

    public void stop() {
        server.stop(0);
    }

    private String generateDashboardHtml() {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Flux Gateway - Intent Filter Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
            background: #f0f4f8;
            color: #1e293b;
            padding: 24px;
            line-height: 1.5;
        }
        .header {
            border-bottom: 2px solid #3b82f6;
            padding-bottom: 12px;
            margin-bottom: 24px;
        }
        h1 { font-size: 24px; color: #1e40af; font-weight: 700; }
        .header p { color: #64748b; margin-top: 6px; font-size: 14px; }
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 16px;
            margin-bottom: 28px;
        }
        .metric-card {
            background: #fff;
            border: 1px solid #e2e8f0;
            padding: 16px;
            border-radius: 8px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.06);
        }
        .metric-label {
            font-size: 11px;
            color: #64748b;
            margin-bottom: 4px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        .metric-value {
            font-size: 26px;
            font-weight: 700;
            color: #1e40af;
        }
        .metric-unit {
            font-size: 13px;
            color: #94a3b8;
        }
        .section {
            background: #fff;
            border: 1px solid #e2e8f0;
            border-radius: 8px;
            padding: 20px;
            margin-top: 24px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.06);
        }
        .section h2 {
            font-size: 16px;
            color: #1e293b;
            margin-bottom: 8px;
            font-weight: 600;
        }
        .section-desc { color: #64748b; font-size: 13px; margin-bottom: 12px; }
        .connection-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(40px, 1fr));
            gap: 6px;
        }
        .connection-cell {
            width: 40px;
            height: 40px;
            border: 1px solid #e2e8f0;
            border-radius: 6px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 10px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
        }
        .connection-cell:hover {
            border-color: #3b82f6;
            transform: scale(1.08);
            box-shadow: 0 2px 6px rgba(59,130,246,0.25);
        }
        .connection-active { background: #3b82f6; color: #fff; border-color: #2563eb; }
        .connection-idle { background: #f1f5f9; color: #94a3b8; }
        .timeline-section {
            background: #fff;
            border: 1px solid #e2e8f0;
            border-radius: 8px;
            padding: 20px;
            margin-top: 24px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.06);
        }
        .timeline-section h2 {
            font-size: 16px;
            color: #1e293b;
            margin-bottom: 8px;
            font-weight: 600;
        }
        .timeline-desc { color: #64748b; font-size: 13px; margin-bottom: 16px; }
        .chart-container {
            background: #f8fafc;
            border: 1px solid #e2e8f0;
            border-radius: 8px;
            padding: 16px;
            height: 220px;
            display: flex;
            align-items: flex-end;
            gap: 3px;
        }
        .chart-bar {
            flex: 1;
            min-width: 4px;
            background: #3b82f6;
            border-radius: 4px 4px 0 0;
            min-height: 4px;
            transition: height 0.2s;
        }
        .chart-bar:hover {
            background: #2563eb;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>⚡ FLUX GATEWAY – INTENT FILTER SYSTEM</h1>
        <p>Real-time Event Dispatch with Bitwise Intent Masking</p>
    </div>

    <div class="metrics-grid">
        <div class="metric-card">
            <div class="metric-label">Events Processed</div>
            <div class="metric-value" id="processed">0</div>
            <div class="metric-unit">total</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Events Sent</div>
            <div class="metric-value" id="sent">0</div>
            <div class="metric-unit">dispatched</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Events Filtered</div>
            <div class="metric-value" id="filtered">0</div>
            <div class="metric-unit">blocked</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Filter Rate</div>
            <div class="metric-value" id="filterRate">0.00</div>
            <div class="metric-unit">%</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Bandwidth Saved</div>
            <div class="metric-value" id="bandwidth">0</div>
            <div class="metric-unit">MB</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Check Latency</div>
            <div class="metric-value" id="latency">0</div>
            <div class="metric-unit">ns</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Active Connections</div>
            <div class="metric-value" id="connections">0</div>
            <div class="metric-unit">users</div>
        </div>
        <div class="metric-card">
            <div class="metric-label">Throughput</div>
            <div class="metric-value" id="throughput">0</div>
            <div class="metric-unit">events/sec</div>
        </div>
    </div>

    <div class="section">
        <h2>Connection Grid</h2>
        <p class="section-desc">Blue = Active | Gray = Idle. Hover for details.</p>
        <div class="connection-grid" id="connectionGrid"></div>
    </div>

    <div class="timeline-section">
        <h2>Event Dispatch Timeline</h2>
        <p class="timeline-desc">Throughput (events/sec) over the last 60 seconds. Hover bars for values.</p>
        <div class="chart-container" id="chart"></div>
    </div>

    <script>
        let lastProcessed = 0;
        let lastTimestamp = Date.now();
        const timelineData = [];
        const timelineMax = 60;

        async function updateMetrics() {
            try {
                const response = await fetch('/metrics');
                const data = await response.json();

                document.getElementById('processed').textContent = data.processed.toLocaleString();
                document.getElementById('sent').textContent = data.sent.toLocaleString();
                document.getElementById('filtered').textContent = data.filtered.toLocaleString();
                document.getElementById('filterRate').textContent = data.filterRate.toFixed(2);
                document.getElementById('bandwidth').textContent = (data.bandwidthSaved / 1024 / 1024).toFixed(2);
                document.getElementById('latency').textContent = data.checkLatency;
                document.getElementById('connections').textContent = data.connections;

                const now = Date.now();
                const elapsed = Math.max((now - lastTimestamp) / 1000, 0.001);
                let throughput = Math.round((data.processed - lastProcessed) / elapsed);
                throughput = Number.isFinite(throughput) && throughput >= 0 ? throughput : 0;
                document.getElementById('throughput').textContent = throughput.toLocaleString();

                timelineData.push(throughput);
                if (timelineData.length > timelineMax) timelineData.shift();
                drawTimeline();

                lastProcessed = data.processed;
                lastTimestamp = now;
            } catch (e) {
                console.error('Failed to fetch metrics:', e);
            }
        }

        function drawTimeline() {
            const chart = document.getElementById('chart');
            chart.innerHTML = '';
            const maxVal = Math.max(1, ...timelineData);
            timelineData.forEach(function (v) {
                const bar = document.createElement('div');
                bar.className = 'chart-bar';
                bar.style.height = Math.max(4, (v / maxVal) * 100) + '%';
                bar.title = v.toLocaleString() + ' events/sec';
                chart.appendChild(bar);
            });
        }

        async function updateConnections() {
            try {
                const response = await fetch('/connections');
                const connections = await response.json();

                const grid = document.getElementById('connectionGrid');
                grid.innerHTML = '';

                connections.forEach(function (conn, idx) {
                    const cell = document.createElement('div');
                    cell.className = 'connection-cell ' +
                        (conn.sent > 0 ? 'connection-active' : 'connection-idle');
                    cell.textContent = idx;
                    cell.title = 'User: ' + conn.userId + ' | Intents: ' + conn.intents + ' | Sent: ' + conn.sent + ' | Filtered: ' + conn.filtered;
                    grid.appendChild(cell);
                });
            } catch (e) {
                console.error('Failed to fetch connections:', e);
            }
        }

        setInterval(updateMetrics, 1000);
        setInterval(updateConnections, 2000);
        updateMetrics();
        updateConnections();
    </script>
</body>
</html>
        """;
    }
}
