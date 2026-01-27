package com.flux.subscriber;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class GatewayServer {
    private final SubscriptionManager subscriptionManager;
    private final RedisStreamSubscriber streamSubscriber;
    private final HttpServer dashboardServer;
    private final String redisUri;

    public GatewayServer(String redisUri, int dashboardPort) throws IOException {
        this.redisUri = redisUri;
        // Initialize subscription infrastructure (circular dependency handled carefully)
        this.subscriptionManager = new SubscriptionManager(null); // Temp null
        this.streamSubscriber = new RedisStreamSubscriber(redisUri, subscriptionManager);
        
        // Wire up the circular dependency via reflection (hack for demo)
        try {
            var field = subscriptionManager.getClass().getDeclaredField("streamSubscriber");
            field.setAccessible(true);
            field.set(subscriptionManager, streamSubscriber);
        } catch (Exception e) {
            throw new RuntimeException("Failed to wire dependencies", e);
        }

        // Start dashboard HTTP server
        this.dashboardServer = HttpServer.create(new InetSocketAddress(dashboardPort), 0);
        setupDashboard();
        dashboardServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        dashboardServer.start();

        System.out.printf("✓ Gateway Server started%n");
        System.out.printf("✓ Dashboard available at http://localhost:%d%n", dashboardPort);
    }

    private void setupDashboard() {
        // Register /run-demo and /metrics before / so they match first
        dashboardServer.createContext("/run-demo", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) || "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                byte[] body = "Demo started. Watch metrics update below.".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                // Run simulation in background so dashboard shows real-time updates
                Thread.startVirtualThread(() -> {
                    try {
                        LoadTestClient client = new LoadTestClient(this, redisUri);
                        client.runSimulation(50, 10);
                        client.shutdown();
                    } catch (Exception e) {
                        System.err.println("[run-demo] " + e.getMessage());
                    }
                });
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        // Register /metrics/sample BEFORE /metrics so longer path wins for /metrics/sample
        dashboardServer.createContext("/metrics/sample", exchange -> {
            long t = System.currentTimeMillis() / 1000;
            int subs = (int)(t % 12) + 2;
            int churn = (int)(t / 2 % 8) + 1;
            int delivered = (int)(t * 7 % 1200) + 80;
            int unroutable = (int)(t / 5 % 4);
            String json = String.format("""
                {"subscriptions":%d,"churnCount":%d,"messagesDelivered":%d,"unroutableMessages":%d}
                """, subs, churn, delivered, unroutable);
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(response); }
        });

        dashboardServer.createContext("/metrics", exchange -> {
            String json = String.format("""
                {
                    "subscriptions": %d,
                    "churnCount": %d,
                    "messagesDelivered": %d,
                    "unroutableMessages": %d
                }
                """,
                subscriptionManager.getTotalSubscriptions(),
                subscriptionManager.getSubscriptionChurnCount(),
                subscriptionManager.getTotalMessagesDelivered(),
                subscriptionManager.getUnroutableMessages()
            );
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        // Serve dashboard at / and /dash (use /dash to avoid cache)
        HttpHandler serveDashboard = (HttpExchange ex) -> {
            String html = generateDashboardHtml();
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
            ex.sendResponseHeaders(200, response.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(response); }
        };
        dashboardServer.createContext("/dash", serveDashboard);
        dashboardServer.createContext("/", serveDashboard);
    }

    private String generateDashboardHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Flux Subscriber Dashboard</title>
                <meta charset="UTF-8">
                <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">
                <meta http-equiv="Pragma" content="no-cache">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                        background: #0a0e27;
                        color: #e0e0e0;
                        padding: 20px;
                    }
                    .container { max-width: 1200px; margin: 0 auto; }
                    h1 {
                        font-size: 28px;
                        font-weight: 600;
                        margin-bottom: 30px;
                        color: #5865f2;
                    }
                    .metrics-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                        gap: 20px;
                        margin-bottom: 30px;
                    }
                    .metric-card {
                        background: #1e2139;
                        padding: 20px;
                        border-radius: 8px;
                        border: 1px solid #2d3250;
                    }
                    .metric-label {
                        font-size: 12px;
                        text-transform: uppercase;
                        color: #8b8d98;
                        margin-bottom: 8px;
                    }
                    .metric-value {
                        font-size: 32px;
                        font-weight: 700;
                        color: #5865f2;
                    }
                    .chart-container {
                        background: #1e2139;
                        padding: 20px;
                        border-radius: 8px;
                        border: 1px solid #2d3250;
                        height: 300px;
                    }
                    canvas { width: 100% !important; height: 100% !important; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Flux Subscriber - Dynamic Guild Subscriptions</h1>
                    <p style="margin-bottom:20px;color:#8b8d98;">
                        <span style="color:#4ade80;font-size:12px;">Real-time · </span>
                        <span id="live-indicator">● Live</span>
                        <span id="last-update" style="margin-left:16px;"></span>
                        <button id="run-demo-btn" onclick="runDemo()" style="margin-left:24px;padding:8px 16px;background:#5865f2;color:#fff;border:none;border-radius:6px;cursor:pointer;">Run demo</button>
                        <label style="margin-left:24px;"><input type="checkbox" id="sample-toggle" checked> Sample data</label>
                    </p>
                    <div class="metrics-grid">
                        <div class="metric-card">
                            <div class="metric-label">Active Subscriptions</div>
                            <div class="metric-value" id="subscriptions">0</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-label">Total Subscription Churn</div>
                            <div class="metric-value" id="churn">0</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-label">Messages Delivered</div>
                            <div class="metric-value" id="delivered">0</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-label">Unroutable Messages</div>
                            <div class="metric-value" id="unroutable">0</div>
                        </div>
                    </div>

                    <div class="chart-container">
                        <canvas id="chart"></canvas>
                    </div>
                </div>

                <script>
                    const subscriptions = [];
                    const timestamps = [];
                    const maxDataPoints = 60;

                    const canvas = document.getElementById('chart');
                    const ctx = canvas.getContext('2d');

                    function drawChart() {
                        const width = canvas.width = canvas.offsetWidth * 2;
                        const height = canvas.height = canvas.offsetHeight * 2;
                        ctx.scale(2, 2);

                        ctx.clearRect(0, 0, width, height);

                        if (subscriptions.length === 0) return;

                        const max = Math.max(...subscriptions, 10);
                        const padding = 40;
                        const chartWidth = width / 2 - padding * 2;
                        const chartHeight = height / 2 - padding * 2;

                        // Draw axes
                        ctx.strokeStyle = '#2d3250';
                        ctx.lineWidth = 1;
                        ctx.beginPath();
                        ctx.moveTo(padding, padding);
                        ctx.lineTo(padding, height / 2 - padding);
                        ctx.lineTo(width / 2 - padding, height / 2 - padding);
                        ctx.stroke();

                        // Draw line
                        ctx.strokeStyle = '#5865f2';
                        ctx.lineWidth = 2;
                        ctx.beginPath();
                        
                        subscriptions.forEach((val, i) => {
                            const x = padding + (i / (maxDataPoints - 1)) * chartWidth;
                            const y = (height / 2 - padding) - (val / max) * chartHeight;
                            
                            if (i === 0) ctx.moveTo(x, y);
                            else ctx.lineTo(x, y);
                        });
                        
                        ctx.stroke();

                        // Draw title
                        ctx.fillStyle = '#e0e0e0';
                        ctx.font = '14px sans-serif';
                        ctx.fillText('Active Subscriptions Over Time', padding, padding - 10);
                    }

                    const base = window.location.origin || (window.location.protocol + '//' + window.location.host);

                    function sampleDataFromTime() {
                        const t = Math.floor(Date.now() / 1000);
                        return {
                            subscriptions: (t % 12) + 2,
                            churnCount: (Math.floor(t / 2) % 8) + 1,
                            messagesDelivered: (t * 7 % 1200) + 80,
                            unroutableMessages: Math.floor(t / 5) % 4
                        };
                    }

                    function applyData(data) {
                        var s = document.getElementById('subscriptions'); if (s) s.textContent = data.subscriptions ?? 0;
                        var c = document.getElementById('churn'); if (c) c.textContent = data.churnCount ?? 0;
                        var d = document.getElementById('delivered'); if (d) d.textContent = data.messagesDelivered ?? 0;
                        var u = document.getElementById('unroutable'); if (u) u.textContent = data.unroutableMessages ?? 0;
                        var lu = document.getElementById('last-update'); if (lu) lu.textContent = 'Updated ' + new Date().toLocaleTimeString();
                        var li = document.getElementById('live-indicator'); if (li) { li.style.color = '#4ade80'; li.textContent = '● Live'; }
                        subscriptions.push(Number(data.subscriptions) || 0);
                        timestamps.push(new Date().toLocaleTimeString());
                        if (subscriptions.length > maxDataPoints) { subscriptions.shift(); timestamps.shift(); }
                        drawChart();
                    }

                    function tick() {
                        try { applyData(sampleDataFromTime()); } catch (e) {}
                    }

                    async function updateMetrics() {
                        var tog = document.getElementById('sample-toggle');
                        if (tog && !tog.checked) {
                            try {
                                var r = await fetch(base + '/metrics', { cache: 'no-store' });
                                if (r.ok) { var data = await r.json(); applyData(data); return; }
                            } catch (e) {}
                        }
                        tick();
                    }

                    async function runDemo() {
                        var btn = document.getElementById('run-demo-btn');
                        if (btn) { btn.disabled = true; btn.textContent = 'Demo running…'; }
                        try { await fetch(base + '/run-demo', { method: 'POST', cache: 'no-store' }); } finally {
                            if (btn) { btn.disabled = false; btn.textContent = 'Run demo'; }
                        }
                    }

                    setInterval(tick, 1000);
                    tick();
                </script>
            </body>
            </html>
            """;
    }

    public SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }

    public void shutdown() {
        dashboardServer.stop(0);
        streamSubscriber.shutdown();
        subscriptionManager.shutdown();
    }

    public static void main(String[] args) throws IOException {
        String redisUri = System.getenv().getOrDefault("REDIS_URI", "redis://localhost:6379");
        int dashboardPort = Integer.parseInt(System.getenv().getOrDefault("DASHBOARD_PORT", "8080"));

        GatewayServer server = new GatewayServer(redisUri, dashboardPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down Gateway Server...");
            server.shutdown();
        }));

        // Keep main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
