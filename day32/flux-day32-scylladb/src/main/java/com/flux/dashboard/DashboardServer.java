package com.flux.dashboard;

import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.flux.model.ChannelStats;
import com.flux.service.MessageService;
import com.flux.service.MetricsCollector;
import com.flux.service.ScyllaConnection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP dashboard using JDK's built-in HTTP server.
 * Serves real-time metrics and cluster visualization.
 */
public class DashboardServer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DashboardServer.class);
    
    private final HttpServer server;
    private final MessageService messageService;
    private final ScyllaConnection connection;
    private final MetricsCollector metrics;

    public DashboardServer(int port, MessageService messageService, 
                          ScyllaConnection connection, MetricsCollector metrics) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.messageService = messageService;
        this.connection = connection;
        this.metrics = metrics;
        
        // Use Virtual Thread executor for handling requests
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        
        // Register routes
        server.createContext("/dashboard", this::serveDashboard);
        server.createContext("/api/stats", this::serveStats);
        server.createContext("/api/channels", this::serveChannels);
        server.createContext("/api/demo", this::serveDemo);
        
        logger.info("Dashboard server initialized on port {}", port);
    }

    public void start() {
        server.start();
        logger.info("Dashboard available at http://localhost:{}/dashboard", 
            ((InetSocketAddress) server.getAddress()).getPort());
    }

    private void serveDashboard(HttpExchange exchange) throws IOException {
        String html = generateDashboardHTML();
        sendResponse(exchange, 200, html, "text/html");
    }

    private void serveStats(HttpExchange exchange) throws IOException {
        String json = String.format("""
            {
                "totalWrites": %d,
                "totalErrors": %d,
                "p99LatencyMicros": %d,
                "writesPerSecond": %.2f
            }
            """,
            metrics.getTotalWrites(),
            metrics.getTotalErrors(),
            metrics.getWriteLatency().getP99(),
            metrics.getWritesPerSecond()
        );
        
        sendResponse(exchange, 200, json, "application/json");
    }
    
    private void serveDemo(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed", "text/plain");
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        int count = 10000;
        if (query != null && query.startsWith("count=")) {
            try {
                count = Integer.parseInt(query.substring(6).split("&")[0]);
            } catch (NumberFormatException ignored) {}
        }
        final int finalCount = count;
        final MessageService svc = messageService;
        final MetricsCollector m = metrics;
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(("{\"status\":\"started\",\"count\":" + finalCount + "}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                for (int i = 0; i < finalCount; i++) {
                    long channelId = (i % 100) + 1;
                    long userId = java.util.concurrent.ThreadLocalRandom.current().nextLong(1, 10000);
                    com.flux.model.Message msg = com.flux.model.Message.create(channelId, userId, "Demo message " + i);
                    long start = System.nanoTime();
                    svc.insertMessage(msg);
                    m.recordWrite(System.nanoTime() - start);
                }
                logger.info("Demo completed: {} messages inserted", finalCount);
            } catch (Exception e) {
                logger.error("Demo failed", e);
                for (int i = 0; i < finalCount; i++) m.recordError();
            }
        });
    }

    private void serveChannels(HttpExchange exchange) throws IOException {
        // Query channels 1-100 (matching LoadTest distribution)
        List<ChannelStats> stats = new ArrayList<>();
        
        for (long channelId = 1; channelId <= 100; channelId++) {
            ChannelStats stat = messageService.getChannelStats(channelId);
            if (stat.messageCount() > 0) {
                stats.add(stat);
            }
        }
        
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < stats.size(); i++) {
            ChannelStats stat = stats.get(i);
            json.append(String.format("""
                {"channelId":%d,"messageCount":%d,"totalBytes":%d}
                """, stat.channelId(), stat.messageCount(), stat.totalBytes()));
            if (i < stats.size() - 1) json.append(",");
        }
        json.append("]");
        
        sendResponse(exchange, 200, json.toString(), "application/json");
    }

    private String generateDashboardHTML() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Flux ScyllaDB Dashboard</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { 
                        font-family: 'Segoe UI', system-ui, sans-serif;
                        background: #0a0e27;
                        color: #e0e0e0;
                        padding: 20px;
                    }
                    .header {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        padding: 30px;
                        border-radius: 8px;
                        margin-bottom: 20px;
                    }
                    .header h1 { color: white; font-size: 2em; }
                    .header p { color: #f0f0f0; margin-top: 10px; }
                    .grid { 
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                        gap: 20px;
                        margin-bottom: 20px;
                    }
                    .card {
                        background: #1a1f3a;
                        border: 1px solid #2d3561;
                        border-radius: 8px;
                        padding: 20px;
                    }
                    .card h2 {
                        color: #667eea;
                        font-size: 1.2em;
                        margin-bottom: 15px;
                        border-bottom: 2px solid #2d3561;
                        padding-bottom: 10px;
                    }
                    .metric {
                        display: flex;
                        justify-content: space-between;
                        margin: 10px 0;
                        padding: 8px;
                        background: rgba(102, 126, 234, 0.1);
                        border-radius: 4px;
                    }
                    .metric-value {
                        font-weight: bold;
                        color: #667eea;
                    }
                    .channel-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fill, minmax(60px, 1fr));
                        gap: 10px;
                    }
                    .channel-box {
                        aspect-ratio: 1;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        background: rgba(102, 126, 234, 0.2);
                        border-radius: 4px;
                        font-size: 0.8em;
                        transition: all 0.3s;
                    }
                    .channel-box:hover {
                        transform: scale(1.05);
                        background: rgba(102, 126, 234, 0.4);
                    }
                    .channel-id { font-weight: bold; }
                    .message-count { 
                        font-size: 0.9em;
                        color: #a0a0a0;
                        margin-top: 4px;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Flux ScyllaDB Dashboard</h1>
                    <p>Real-time Wide-Column Store Metrics</p>
                </div>
                
                <div class="grid">
                    <div class="card">
                        <h2>Write Performance</h2>
                        <div class="metric">
                            <span>Total Writes</span>
                            <span class="metric-value" id="totalWrites">0</span>
                        </div>
                        <div class="metric">
                            <span>P99 Latency (us)</span>
                            <span class="metric-value" id="p99Latency">0</span>
                        </div>
                        <div class="metric">
                            <span>Writes/sec</span>
                            <span class="metric-value" id="writesPerSec">0</span>
                        </div>
                        <div class="metric">
                            <span>Error Count</span>
                            <span class="metric-value" id="errorCount">0</span>
                        </div>
                    </div>
                    
                    <div class="card">
                        <h2>Database Health</h2>
                        <div class="metric">
                            <span>Cluster Status</span>
                            <span class="metric-value">Online</span>
                        </div>
                        <div class="metric">
                            <span>Replication Factor</span>
                            <span class="metric-value">1 (Local Dev)</span>
                        </div>
                        <div class="metric">
                            <span>Active Partitions</span>
                            <span class="metric-value" id="activePartitions">0</span>
                        </div>
                    </div>
                </div>
                
                <div class="card">
                    <h2>Channel Distribution (Partition Heatmap)</h2>
                    <div class="channel-grid" id="channelGrid"></div>
                </div>
                
                <script>
                    async function updateStats() {
                        try {
                            const response = await fetch('/api/stats');
                            const data = await response.json();
                            
                            document.getElementById('totalWrites').textContent = 
                                data.totalWrites.toLocaleString();
                            document.getElementById('p99Latency').textContent = 
                                data.p99LatencyMicros.toLocaleString();
                            document.getElementById('writesPerSec').textContent = 
                                data.writesPerSecond.toFixed(2);
                            document.getElementById('errorCount').textContent = 
                                data.totalErrors.toLocaleString();
                        } catch (error) {
                            console.error('Failed to fetch stats:', error);
                        }
                    }
                    
                    async function updateChannels() {
                        try {
                            const response = await fetch('/api/channels');
                            const channels = await response.json();
                            
                            const grid = document.getElementById('channelGrid');
                            grid.innerHTML = '';
                            
                            document.getElementById('activePartitions').textContent = 
                                channels.length;
                            
                            channels.forEach(channel => {
                                const box = document.createElement('div');
                                box.className = 'channel-box';
                                
                                const opacity = Math.min(channel.messageCount / 1000, 1);
                                box.style.background = 
                                    `rgba(102, 126, 234, ${0.2 + opacity * 0.6})`;
                                
                                box.innerHTML = `
                                    <div class="channel-id">#${channel.channelId}</div>
                                    <div class="message-count">${channel.messageCount}</div>
                                `;
                                grid.appendChild(box);
                            });
                        } catch (error) {
                            console.error('Failed to fetch channels:', error);
                        }
                    }
                    
                    // Update every 2 seconds
                    setInterval(() => {
                        updateStats();
                        updateChannels();
                    }, 2000);
                    
                    // Initial load
                    updateStats();
                    updateChannels();
                </script>
            </body>
            </html>
            """;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, 
                             String response, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        logger.info("Dashboard server stopped");
    }
}
