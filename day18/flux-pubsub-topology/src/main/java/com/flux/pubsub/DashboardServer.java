package com.flux.pubsub;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight HTTP server for real-time topology dashboard.
 */
public class DashboardServer {
    private final HttpServer server;
    private final LocalPubSubBroker broker;
    
    public DashboardServer(int port, LocalPubSubBroker broker) throws IOException {
        this.broker = broker;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/", exchange -> {
            String response = generateDashboardHTML();
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
                os.flush();
            }
        });
        
        server.createContext("/stats", exchange -> {
            String stats = String.format(
                "{\"topics\":%d,\"subscribers\":%d,\"publications\":%d,\"drops\":%d}",
                broker.topicCount(),
                broker.totalSubscribers(),
                broker.publicationCount(),
                broker.dropCount()
            );
            byte[] statsBytes = stats.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, statsBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(statsBytes);
                os.flush();
            }
        });
        
        server.createContext("/benchmark", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String type = query != null && query.contains("type=user") ? "user" : "guild";
            
            var result = type.equals("user") 
                ? TopologyComparison.benchmarkUserCentric(10, 100, 10)
                : TopologyComparison.benchmarkGuildCentric(10, 100, 10);
            
            String json = String.format(
                "{\"topology\":\"%s\",\"duration\":%d,\"publications\":%d,\"throughput\":%d,\"memory\":%d}",
                result.topology(),
                result.durationMs(),
                result.messagesPublished(),
                result.throughputMsgPerSec(),
                result.memoryUsedMB()
            );
            
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, jsonBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsonBytes);
                os.flush();
            }
        });
        
        server.createContext("/demo", exchange -> {
            // Publish messages to all guild topics to demonstrate the system
            int messageCount = 0;
            for (int guild = 0; guild < 10; guild++) {
                String guildTopic = "guild:" + guild;
                byte[] message = ("Demo message " + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
                broker.publish(guildTopic, message);
                messageCount++;
            }
            
            String response = String.format("{\"status\":\"ok\",\"messages_published\":%d}", messageCount);
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
                os.flush();
            }
        });
    }
    
    public void start() {
        server.start();
        System.out.println("📊 Dashboard running at http://localhost:" + server.getAddress().getPort() + "/");
    }
    
    public void stop() {
        server.stop(0);
    }
    
    private String generateDashboardHTML() {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">
    <meta http-equiv="Pragma" content="no-cache">
    <meta http-equiv="Expires" content="0">
    <title>Flux PubSub Topology Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            font-family: 'Monaco', 'Courier New', monospace; 
            background: #0a0e27; 
            color: #00ff41;
            padding: 20px;
        }
        .container { max-width: 1400px; margin: 0 auto; }
        h1 { 
            font-size: 24px; 
            margin-bottom: 30px; 
            text-align: center;
            text-transform: uppercase;
            letter-spacing: 3px;
        }
        .grid { 
            display: grid; 
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); 
            gap: 20px;
            margin-bottom: 30px;
        }
        .card {
            background: #1a1f3a;
            border: 2px solid #00ff41;
            border-radius: 8px;
            padding: 20px;
        }
        .metric-label { 
            font-size: 12px; 
            opacity: 0.7; 
            margin-bottom: 5px;
        }
        .metric-value { 
            font-size: 36px; 
            font-weight: bold;
            text-shadow: 0 0 10px #00ff41;
        }
        .btn {
            background: #00ff41;
            color: #0a0e27;
            border: none;
            padding: 12px 24px;
            font-family: inherit;
            font-size: 14px;
            font-weight: bold;
            cursor: pointer;
            border-radius: 4px;
            margin: 5px;
            text-transform: uppercase;
            transition: all 0.3s;
        }
        .btn:hover { 
            background: #00cc33;
            transform: scale(1.05);
        }
        .btn:active { transform: scale(0.95); }
        .benchmark-result {
            background: #0f1428;
            padding: 15px;
            margin: 10px 0;
            border-left: 4px solid #00ff41;
            font-size: 14px;
        }
        .comparison {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
            margin-top: 20px;
        }
        .bar-chart {
            height: 200px;
            display: flex;
            align-items: flex-end;
            gap: 20px;
            padding: 20px;
            background: #0f1428;
            border-radius: 4px;
        }
        .bar {
            flex: 1;
            background: linear-gradient(to top, #00ff41, #00cc33);
            position: relative;
            transition: height 0.5s;
        }
        .bar-label {
            position: absolute;
            bottom: -25px;
            left: 50%;
            transform: translateX(-50%);
            font-size: 12px;
        }
        .bar-value {
            position: absolute;
            top: -25px;
            left: 50%;
            transform: translateX(-50%);
            font-weight: bold;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 Flux PubSub Topology Dashboard</h1>
        
        <div class="grid">
            <div class="card">
                <div class="metric-label">ACTIVE TOPICS</div>
                <div class="metric-value" id="topics">0</div>
            </div>
            <div class="card">
                <div class="metric-label">TOTAL SUBSCRIBERS</div>
                <div class="metric-value" id="subscribers">0</div>
            </div>
            <div class="card">
                <div class="metric-label">PUBLICATIONS</div>
                <div class="metric-value" id="publications">0</div>
            </div>
            <div class="card">
                <div class="metric-label">DROPPED MESSAGES</div>
                <div class="metric-value" id="drops">0</div>
            </div>
        </div>
        
        <div class="card">
            <h2 style="margin-bottom: 20px;">Topology Comparison</h2>
            <div style="text-align: center; margin-bottom: 20px;">
                <button class="btn" onclick="runBenchmark('user')">Test User-Centric</button>
                <button class="btn" onclick="runBenchmark('guild')">Test Guild-Centric</button>
                <button class="btn" onclick="runComparison()">Run Side-by-Side</button>
            </div>
            <div id="results"></div>
            <div class="bar-chart" id="chart">
                <div class="bar" style="height: 0%;">
                    <span class="bar-value"></span>
                    <span class="bar-label">User-Centric</span>
                </div>
                <div class="bar" style="height: 0%;">
                    <span class="bar-value"></span>
                    <span class="bar-label">Guild-Centric</span>
                </div>
            </div>
        </div>
    </div>
    
    <script>
        function updateStats() {
            fetch('/stats')
                .then(r => r.json())
                .then(data => {
                    document.getElementById('topics').textContent = data.topics.toLocaleString();
                    document.getElementById('subscribers').textContent = data.subscribers.toLocaleString();
                    document.getElementById('publications').textContent = data.publications.toLocaleString();
                    document.getElementById('drops').textContent = data.drops.toLocaleString();
                });
        }
        
        function runBenchmark(type) {
            const resultsDiv = document.getElementById('results');
            resultsDiv.innerHTML = '<div class="benchmark-result">Running benchmark...</div>';
            
            fetch(`/benchmark?type=${type}`)
                .then(r => r.json())
                .then(data => {
                    resultsDiv.innerHTML = `
                        <div class="benchmark-result">
                            <strong>${data.topology.toUpperCase()}</strong><br>
                            Duration: ${data.duration}ms<br>
                            Publications: ${data.publications.toLocaleString()}<br>
                            Throughput: ${data.throughput.toLocaleString()} msg/sec<br>
                            Memory: ${data.memory} MB
                        </div>
                    `;
                });
        }
        
        async function runComparison() {
            const resultsDiv = document.getElementById('results');
            resultsDiv.innerHTML = '<div class="benchmark-result">Running comparison...</div>';
            
            const userResult = await fetch('/benchmark?type=user').then(r => r.json());
            const guildResult = await fetch('/benchmark?type=guild').then(r => r.json());
            
            const speedup = (userResult.duration / guildResult.duration).toFixed(1);
            
            resultsDiv.innerHTML = `
                <div class="comparison">
                    <div class="benchmark-result">
                        <strong>USER-CENTRIC</strong><br>
                        Duration: ${userResult.duration}ms<br>
                        Publications: ${userResult.publications.toLocaleString()}<br>
                        Throughput: ${userResult.throughput.toLocaleString()} msg/sec<br>
                        Memory: ${userResult.memory} MB
                    </div>
                    <div class="benchmark-result">
                        <strong>GUILD-CENTRIC</strong><br>
                        Duration: ${guildResult.duration}ms<br>
                        Publications: ${guildResult.publications.toLocaleString()}<br>
                        Throughput: ${guildResult.throughput.toLocaleString()} msg/sec<br>
                        Memory: ${guildResult.memory} MB<br>
                        <br>
                        <strong style="color: #00ff41;">${speedup}× FASTER</strong>
                    </div>
                </div>
            `;
            
            // Update bar chart
            const maxThroughput = Math.max(userResult.throughput, guildResult.throughput);
            const bars = document.querySelectorAll('.bar');
            bars[0].style.height = (userResult.throughput / maxThroughput * 100) + '%';
            bars[0].querySelector('.bar-value').textContent = (userResult.throughput / 1000).toFixed(1) + 'K msg/s';
            bars[1].style.height = (guildResult.throughput / maxThroughput * 100) + '%';
            bars[1].querySelector('.bar-value').textContent = (guildResult.throughput / 1000).toFixed(1) + 'K msg/s';
        }
        
        setInterval(updateStats, 1000);
        updateStats();
    </script>
</body>
</html>
""";
    }
}
