package com.flux.gateway;

import com.flux.typing.TypingIndicatorService;
import com.flux.metrics.Metrics;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class DashboardServer {
    private final HttpServer server;
    private final TypingIndicatorService typingService;
    private final Metrics metrics;
    
    public DashboardServer(int port, TypingIndicatorService typingService, Metrics metrics) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.typingService = typingService;
        this.metrics = metrics;
        
        server.createContext("/", this::handleDashboard);
        server.createContext("/api/metrics", this::handleMetrics);
        server.createContext("/api/typers", this::handleTypers);
        server.createContext("/api/simulate", this::handleSimulate);
        server.setExecutor(null); // Use default executor
    }
    
    public void start() {
        server.start();
        System.out.println("[INFO] Dashboard HTTP server on http://localhost:" + server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = """
<!DOCTYPE html>
<html>
<head>
    <title>Flux Typing Indicators Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            font-family: 'Courier New', monospace; 
            background: #0a0a0a; 
            color: #00ff00; 
            padding: 20px;
        }
        .header { 
            border-bottom: 2px solid #00ff00; 
            padding-bottom: 10px; 
            margin-bottom: 20px;
        }
        .metrics { 
            display: grid; 
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); 
            gap: 15px; 
            margin-bottom: 20px;
        }
        .metric-box { 
            border: 1px solid #00ff00; 
            padding: 15px; 
            background: #0f0f0f;
        }
        .metric-label { font-size: 12px; opacity: 0.7; }
        .metric-value { font-size: 28px; font-weight: bold; margin-top: 5px; }
        .typing-grid { 
            border: 1px solid #00ff00; 
            padding: 15px; 
            min-height: 200px;
            background: #0f0f0f;
        }
        .typer-badge { 
            display: inline-block; 
            background: #003300; 
            border: 1px solid #00ff00;
            padding: 5px 10px; 
            margin: 5px; 
            border-radius: 3px;
        }
        .controls { margin: 20px 0; }
        button { 
            background: #003300; 
            color: #00ff00; 
            border: 1px solid #00ff00; 
            padding: 10px 20px; 
            cursor: pointer; 
            margin-right: 10px;
            font-family: 'Courier New', monospace;
        }
        button:hover { background: #00ff00; color: #000; }
        .status { 
            padding: 10px; 
            border: 1px solid #00ff00; 
            margin-top: 20px;
            background: #0f0f0f;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>⚡ FLUX TYPING INDICATORS - LIVE DASHBOARD</h1>
    </div>
    
    <div class="metrics">
        <div class="metric-box">
            <div class="metric-label">PUBLISHED EVENTS</div>
            <div class="metric-value" id="published">0</div>
        </div>
        <div class="metric-box">
            <div class="metric-label">THROTTLED</div>
            <div class="metric-value" id="throttled">0</div>
        </div>
        <div class="metric-box">
            <div class="metric-label">DROPPED</div>
            <div class="metric-value" id="dropped">0</div>
        </div>
        <div class="metric-box">
            <div class="metric-label">RING SATURATION</div>
            <div class="metric-value" id="saturation">0%</div>
        </div>
    </div>
    
    <div class="controls">
        <button onclick="simulate(10)">Simulate 10 Typers</button>
        <button onclick="simulate(50)">Simulate 50 Typers</button>
        <button onclick="simulate(100)">Simulate 100 Typers</button>
        <button onclick="clearStatus()">Clear Status</button>
    </div>
    
    <h2>Active Typers in #general (Channel ID: 1001)</h2>
    <div class="typing-grid" id="typers">
        <em>No one is typing...</em>
    </div>
    
    <div class="status" id="status">
        Ready. Click a button to simulate typing events.
    </div>
    
    <script>
        async function fetchMetrics() {
            const res = await fetch('/api/metrics');
            const data = await res.json();
            document.getElementById('published').textContent = data.published;
            document.getElementById('throttled').textContent = data.throttled;
            document.getElementById('dropped').textContent = data.dropped;
            document.getElementById('saturation').textContent = data.saturation + '%';
        }
        
        async function fetchTypers() {
            const res = await fetch('/api/typers?channel=1001');
            const typers = await res.json();
            const container = document.getElementById('typers');
            
            if (typers.length === 0) {
                container.innerHTML = '<em>No one is typing...</em>';
            } else {
                container.innerHTML = typers.map(id => 
                    `<span class="typer-badge">User #${id}</span>`
                ).join('');
            }
        }
        
        function simulate(count) {
            document.getElementById('status').textContent = 
                `Simulating ${count} concurrent typers for 10 seconds...`;
            
            fetch(`/api/simulate?count=${count}&channel=1001`)
                .then(() => {
                    document.getElementById('status').textContent = 
                        `✓ Simulation complete. ${count} virtual typers generated events.`;
                });
        }
        
        function clearStatus() {
            document.getElementById('status').textContent = 'Status cleared.';
        }
        
        // Poll every 500ms
        setInterval(() => {
            fetchMetrics();
            fetchTypers();
        }, 500);
        
        // Initial load
        fetchMetrics();
        fetchTypers();
    </script>
</body>
</html>
        """;
        
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
    
    private void handleMetrics(HttpExchange exchange) throws IOException {
        long published = metrics.getPublished();
        long throttled = metrics.getThrottled();
        long dropped = metrics.getDropped();
        double saturation = typingService.getRing().getSaturation() * 100;
        
        String json = String.format(
            "{\"published\":%d,\"throttled\":%d,\"dropped\":%d,\"saturation\":%.2f}",
            published, throttled, dropped, saturation
        );
        
        sendJson(exchange, json);
    }
    
    private void handleSimulate(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        int count = 50;
        long channelId = 1001;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("count=")) {
                    count = Math.min(500, Math.max(1, Integer.parseInt(param.substring(6))));
                } else if (param.startsWith("channel=")) {
                    channelId = Long.parseLong(param.substring(8));
                }
            }
        }
        final int finalCount = count;
        final long finalChannelId = channelId;
        Thread.ofVirtual().start(() -> {
            java.util.Random rand = new java.util.Random();
            long endTime = System.currentTimeMillis() + 10000;
            while (System.currentTimeMillis() < endTime) {
                for (int i = 0; i < finalCount; i++) {
                    long userId = 10000 + rand.nextInt(finalCount * 2);
                    typingService.handleTypingEvent(userId, finalChannelId);
                }
                try { Thread.sleep(200); } catch (InterruptedException e) { break; }
            }
        });
        sendJson(exchange, "{\"status\":\"started\",\"count\":" + count + "}");
    }
    
    private void handleTypers(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        long channelId = 1001; // Default
        
        if (query != null && query.startsWith("channel=")) {
            channelId = Long.parseLong(query.substring(8));
        }
        
        long[] typers = typingService.getActiveTypers(channelId);
        
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < typers.length; i++) {
            if (i > 0) json.append(",");
            json.append(typers[i]);
        }
        json.append("]");
        
        sendJson(exchange, json.toString());
    }
    
    private void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
