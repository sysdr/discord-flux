package com.flux.publisher.api;

import com.flux.publisher.metrics.MetricsCollector;
import com.flux.publisher.ratelimit.TokenBucketRateLimiter;
import com.flux.publisher.redis.RedisPublisher;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Real-time dashboard showing:
 * - Publish rate and latency
 * - Rate limiter status
 * - Redis connection health
 * - Per-guild metrics
 */
public class DashboardHandler implements HttpHandler {
    
    private final MetricsCollector metrics;
    private final TokenBucketRateLimiter rateLimiter;
    private final RedisPublisher publisher;
    private final Gson gson = new Gson();

    public DashboardHandler(MetricsCollector metrics, 
                           TokenBucketRateLimiter rateLimiter,
                           RedisPublisher publisher) {
        this.metrics = metrics;
        this.rateLimiter = rateLimiter;
        this.publisher = publisher;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        
        if (path.equals("/api/metrics")) {
            handleMetricsApi(exchange);
        } else {
            handleDashboard(exchange);
        }
    }

    private void handleMetricsApi(HttpExchange exchange) throws IOException {
        var snapshot = metrics.getSnapshot();
        var redisStats = publisher.getStats();
        
        var response = new DashboardData(
            snapshot.totalPublished(),
            snapshot.totalErrors(),
            snapshot.totalRateLimited(),
            snapshot.avgLatencyNanos() / 1_000_000.0, // Convert to ms
            rateLimiter.availableTokens(),
            snapshot.activeGuilds(),
            redisStats.connectionOpen()
        );
        
        String json = gson.toJson(response);
        sendResponse(exchange, 200, "application/json", json);
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Flux Publisher Dashboard</title>
                <style>
                    body {
                        font-family: 'Segoe UI', system-ui, sans-serif;
                        background: #1a1a1a;
                        color: #e0e0e0;
                        margin: 0;
                        padding: 20px;
                    }
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                    }
                    h1 {
                        color: #00d9ff;
                        margin-bottom: 30px;
                    }
                    .metrics-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                        gap: 20px;
                        margin-bottom: 30px;
                    }
                    .metric-card {
                        background: #2a2a2a;
                        border: 1px solid #3a3a3a;
                        border-radius: 8px;
                        padding: 20px;
                    }
                    .metric-label {
                        font-size: 14px;
                        color: #888;
                        margin-bottom: 8px;
                    }
                    .metric-value {
                        font-size: 32px;
                        font-weight: bold;
                        color: #00d9ff;
                    }
                    .metric-unit {
                        font-size: 16px;
                        color: #888;
                        margin-left: 4px;
                    }
                    .status-indicator {
                        display: inline-block;
                        width: 12px;
                        height: 12px;
                        border-radius: 50%;
                        margin-right: 8px;
                    }
                    .status-healthy { background: #00ff88; }
                    .status-warning { background: #ffaa00; }
                    .status-critical { background: #ff4444; }
                    .controls {
                        margin-top: 30px;
                    }
                    button {
                        background: #00d9ff;
                        color: #1a1a1a;
                        border: none;
                        padding: 12px 24px;
                        border-radius: 6px;
                        font-size: 16px;
                        font-weight: bold;
                        cursor: pointer;
                        margin-right: 10px;
                    }
                    button:hover {
                        background: #00b8d4;
                    }
                    .chart {
                        background: #2a2a2a;
                        border: 1px solid #3a3a3a;
                        border-radius: 8px;
                        padding: 20px;
                        margin-top: 20px;
                        height: 200px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Flux Publisher Dashboard</h1>
                    
                    <div class="metrics-grid">
                        <div class="metric-card">
                            <div class="metric-label">Total Published</div>
                            <div class="metric-value" id="total-published">0</div>
                        </div>
                        
                        <div class="metric-card">
                            <div class="metric-label">Publish Rate</div>
                            <div class="metric-value">
                                <span id="publish-rate">0</span>
                                <span class="metric-unit">msg/sec</span>
                            </div>
                        </div>
                        
                        <div class="metric-card">
                            <div class="metric-label">Avg Latency</div>
                            <div class="metric-value">
                                <span id="avg-latency">0</span>
                                <span class="metric-unit">ms</span>
                            </div>
                        </div>
                        
                        <div class="metric-card">
                            <div class="metric-label">Rate Limited</div>
                            <div class="metric-value" id="rate-limited">0</div>
                        </div>
                        
                        <div class="metric-card">
                            <div class="metric-label">Errors</div>
                            <div class="metric-value" id="errors">0</div>
                        </div>
                        
                        <div class="metric-card">
                            <div class="metric-label">Available Tokens</div>
                            <div class="metric-value" id="tokens">0</div>
                        </div>
                        
                        <div class="metric-card">
                            <div class="metric-label">Active Guilds</div>
                            <div class="metric-value" id="active-guilds">0</div>
                        </div>
                        
                        <div class="metric-card">
                            <div class="metric-label">Redis Status</div>
                            <div class="metric-value">
                                <span class="status-indicator" id="redis-status"></span>
                                <span id="redis-text">Unknown</span>
                            </div>
                        </div>
                    </div>
                    
                    <div class="controls">
                        <button onclick="triggerRateLimit()">Trigger Rate Limit</button>
                        <button onclick="resetMetrics()">Reset Metrics</button>
                    </div>
                </div>
                
                <script>
                    let lastPublished = 0;
                    let lastUpdate = Date.now();
                    
                    async function updateMetrics() {
                        try {
                            const response = await fetch('/api/metrics');
                            const data = await response.json();
                            
                            // Calculate publish rate
                            const now = Date.now();
                            const timeDelta = (now - lastUpdate) / 1000;
                            const publishDelta = data.totalPublished - lastPublished;
                            const rate = timeDelta > 0 ? Math.round(publishDelta / timeDelta) : 0;
                            
                            lastPublished = data.totalPublished;
                            lastUpdate = now;
                            
                            // Update metrics
                            document.getElementById('total-published').textContent = 
                                data.totalPublished.toLocaleString();
                            document.getElementById('publish-rate').textContent = rate;
                            document.getElementById('avg-latency').textContent = 
                                data.avgLatencyMs.toFixed(2);
                            document.getElementById('rate-limited').textContent = 
                                data.totalRateLimited.toLocaleString();
                            document.getElementById('errors').textContent = 
                                data.totalErrors.toLocaleString();
                            document.getElementById('tokens').textContent = 
                                data.availableTokens.toLocaleString();
                            document.getElementById('active-guilds').textContent = 
                                data.activeGuilds;
                            
                            // Update Redis status
                            const statusEl = document.getElementById('redis-status');
                            const textEl = document.getElementById('redis-text');
                            if (data.redisConnected) {
                                statusEl.className = 'status-indicator status-healthy';
                                textEl.textContent = 'Connected';
                            } else {
                                statusEl.className = 'status-indicator status-critical';
                                textEl.textContent = 'Disconnected';
                            }
                        } catch (err) {
                            console.error('Failed to fetch metrics:', err);
                        }
                    }
                    
                    async function triggerRateLimit() {
                        // Throttled burst: batches of 20, 15ms delay between batches
                        // avoids ERR_INSUFFICIENT_RESOURCES from 1000+ simultaneous fetches
                        const BATCH = 20;
                        const TOTAL = 200;
                        const DELAY_MS = 15;
                        const payload = JSON.stringify({
                            guild_id: 'burst-test',
                            channel_id: 'test',
                            user_id: 'test',
                            content: 'Burst test message'
                        });
                        for (let i = 0; i < TOTAL; i += BATCH) {
                            for (let j = 0; j < BATCH && i + j < TOTAL; j++) {
                                fetch('/messages', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: payload
                                }).catch(() => {});
                            }
                            if (i + BATCH < TOTAL) await new Promise(r => setTimeout(r, DELAY_MS));
                        }
                    }
                    
                    function resetMetrics() {
                        fetch('/api/reset', { method: 'POST' })
                            .then(() => updateMetrics());
                    }
                    
                    // Update every 500ms
                    setInterval(updateMetrics, 500);
                    updateMetrics();
                </script>
            </body>
            </html>
            """;
        
        sendResponse(exchange, 200, "text/html", html);
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType, String body) 
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    record DashboardData(
        long totalPublished,
        long totalErrors,
        long totalRateLimited,
        double avgLatencyMs,
        long availableTokens,
        int activeGuilds,
        boolean redisConnected
    ) {}
}
