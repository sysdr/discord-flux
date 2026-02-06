package com.flux.persistence;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class SimulatorServer {
    private final CoordinatorNode coordinator;
    private final MetricsCollector metrics;
    private final SnowflakeGenerator idGen;
    private final Gson gson = new Gson();
    private HttpServer server;
    
    public SimulatorServer(CoordinatorNode coordinator, MetricsCollector metrics) {
        this.coordinator = coordinator;
        this.metrics = metrics;
        this.idGen = new SnowflakeGenerator(1);
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        
        server.createContext("/", exchange -> {
            byte[] response = getDashboardHtml().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.createContext("/api/write", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String level = query != null && query.contains("level=QUORUM") 
                ? "QUORUM" : "ONE";
            
            ConsistencyLevel cl = ConsistencyLevel.valueOf(level);
            Message msg = Message.create("channel-1", "user-1", "Test message", idGen);
            WriteResult result = coordinator.write(msg, cl);
            metrics.recordWrite(cl, result);
            
            String json = gson.toJson(Map.of(
                "success", result.success(),
                "latency", result.latencyMs(),
                "level", level,
                "messageId", msg.id()
            ));
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes());
            }
        });
        
        server.createContext("/api/partition", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            boolean enable = query != null && query.contains("enable=true");
            
            // Partition the last replica
            List<ReplicaNode> replicas = coordinator.getReplicas();
            replicas.get(replicas.size() - 1).setPartitioned(enable);
            
            String json = gson.toJson(Map.of("partitioned", enable));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes());
            }
        });
        
        server.createContext("/api/metrics", exchange -> {
            MetricsCollector.MetricsSnapshot snapshot = metrics.getSnapshot();
            String json = gson.toJson(snapshot);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes());
            }
        });
        
        server.start();
        System.out.println("🎯 Dashboard: http://localhost:8080");
        System.out.println("📊 Metrics: http://localhost:8080/api/metrics");
        
        // Seed metrics so dashboard shows non-zero values on first load
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Thread.sleep(500);
                for (int i = 0; i < 30; i++) {
                    ConsistencyLevel level = i % 2 == 0 ? ConsistencyLevel.ONE : ConsistencyLevel.QUORUM;
                    Message msg = Message.create("channel-1", "user-seed", "Seed message " + i, idGen);
                    WriteResult result = coordinator.write(msg, level);
                    metrics.recordWrite(level, result);
                    Thread.sleep(10);
                }
                System.out.println("📈 Seeded 30 writes for dashboard metrics");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
    
    private String getDashboardHtml() {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Flux Consistency Simulator</title>
            <style>
                body { 
                    font-family: 'Courier New', monospace; 
                    background: #0a0e27; 
                    color: #00ff41;
                    padding: 20px;
                }
                .container { max-width: 1200px; margin: 0 auto; }
                h1 { color: #00ff41; text-shadow: 0 0 10px #00ff41; }
                .controls { 
                    background: #1a1f3a; 
                    padding: 20px; 
                    border: 2px solid #00ff41;
                    margin-bottom: 20px;
                    border-radius: 8px;
                }
                button {
                    background: #00ff41;
                    color: #0a0e27;
                    border: none;
                    padding: 10px 20px;
                    margin: 5px;
                    cursor: pointer;
                    font-weight: bold;
                    border-radius: 4px;
                }
                button:hover { background: #00cc33; }
                .metrics {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 20px;
                }
                .metric-box {
                    background: #1a1f3a;
                    padding: 15px;
                    border: 2px solid #00ff41;
                    border-radius: 8px;
                }
                .metric-box h3 { margin-top: 0; color: #00ff41; }
                .stat { 
                    display: flex; 
                    justify-content: space-between;
                    padding: 5px 0;
                    border-bottom: 1px solid #2a3f5a;
                }
                .latency-bar {
                    height: 30px;
                    background: linear-gradient(to right, #00ff41, #ff4500);
                    margin: 10px 0;
                    border-radius: 4px;
                    position: relative;
                }
                .latency-value {
                    position: absolute;
                    right: 10px;
                    top: 50%;
                    transform: translateY(-50%);
                    color: #0a0e27;
                    font-weight: bold;
                }
                .replica-grid {
                    display: grid;
                    grid-template-columns: repeat(3, 1fr);
                    gap: 10px;
                    margin-top: 20px;
                }
                .replica {
                    background: #1a1f3a;
                    padding: 15px;
                    border: 2px solid #00ff41;
                    text-align: center;
                    border-radius: 8px;
                }
                .replica.partitioned { 
                    border-color: #ff4500;
                    opacity: 0.5;
                }
                #log {
                    background: #000;
                    color: #00ff41;
                    padding: 15px;
                    height: 200px;
                    overflow-y: scroll;
                    font-family: 'Courier New', monospace;
                    font-size: 12px;
                    border: 2px solid #00ff41;
                    border-radius: 8px;
                    margin-top: 20px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>⚡ FLUX CONSISTENCY SIMULATOR</h1>
                
                <div class="controls">
                    <h3>Write Operations</h3>
                    <button onclick="writeMessage('ONE')">Write with ONE</button>
                    <button onclick="writeMessage('QUORUM')">Write with QUORUM</button>
                    <button onclick="togglePartition()">Toggle Partition (Node 3)</button>
                    <button onclick="runLoadTest()">Run Load Test (100 writes)</button>
                </div>
                
                <div class="metrics">
                    <div class="metric-box">
                        <h3>ONE Consistency</h3>
                        <div class="stat">
                            <span>Avg Latency:</span>
                            <span id="one-avg">0ms</span>
                        </div>
                        <div class="stat">
                            <span>P50:</span>
                            <span id="one-p50">0ms</span>
                        </div>
                        <div class="stat">
                            <span>P99:</span>
                            <span id="one-p99">0ms</span>
                        </div>
                        <div class="latency-bar">
                            <div id="one-bar" style="width: 0%; background: #00ff41; height: 100%; border-radius: 4px;"></div>
                            <div class="latency-value" id="one-max">0ms</div>
                        </div>
                    </div>
                    
                    <div class="metric-box">
                        <h3>QUORUM Consistency</h3>
                        <div class="stat">
                            <span>Avg Latency:</span>
                            <span id="quorum-avg">0ms</span>
                        </div>
                        <div class="stat">
                            <span>P50:</span>
                            <span id="quorum-p50">0ms</span>
                        </div>
                        <div class="stat">
                            <span>P99:</span>
                            <span id="quorum-p99">0ms</span>
                        </div>
                        <div class="latency-bar">
                            <div id="quorum-bar" style="width: 0%; background: #00ff41; height: 100%; border-radius: 4px;"></div>
                            <div class="latency-value" id="quorum-max">0ms</div>
                        </div>
                    </div>
                </div>
                
                <div class="metric-box" style="margin-top: 20px;">
                    <h3>Cluster Health</h3>
                    <div class="stat">
                        <span>Total Writes:</span>
                        <span id="total-writes">0</span>
                    </div>
                    <div class="stat">
                        <span>Failed Writes:</span>
                        <span id="failed-writes" style="color: #ff4500;">0</span>
                    </div>
                    <div class="stat">
                        <span>Stale Reads:</span>
                        <span id="stale-reads" style="color: #ffaa00;">0</span>
                    </div>
                </div>
                
                <div class="replica-grid">
                    <div class="replica" id="replica-1">
                        <h4>Replica 1</h4>
                        <div>Status: <span style="color: #00ff41;">ONLINE</span></div>
                    </div>
                    <div class="replica" id="replica-2">
                        <h4>Replica 2</h4>
                        <div>Status: <span style="color: #00ff41;">ONLINE</span></div>
                    </div>
                    <div class="replica" id="replica-3">
                        <h4>Replica 3</h4>
                        <div>Status: <span style="color: #00ff41;">ONLINE</span></div>
                    </div>
                </div>
                
                <div id="log"></div>
            </div>
            
            <script>
                let isPartitioned = false;
                
                function log(msg) {
                    const logDiv = document.getElementById('log');
                    const time = new Date().toISOString().split('T')[1].split('.')[0];
                    logDiv.innerHTML += `[${time}] ${msg}\\n`;
                    logDiv.scrollTop = logDiv.scrollHeight;
                }
                
                async function writeMessage(level) {
                    log(`Initiating write with ${level}...`);
                    const start = Date.now();
                    
                    try {
                        const response = await fetch(`/api/write?level=${level}`);
                        const data = await response.json();
                        const elapsed = Date.now() - start;
                        
                        if (data.success) {
                            log(`✓ Write succeeded in ${data.latency}ms (${level}) - ID: ${data.messageId}`);
                        } else {
                            log(`✗ Write failed after ${elapsed}ms`);
                        }
                        
                        updateMetrics();
                    } catch (error) {
                        log(`✗ Error: ${error.message}`);
                    }
                }
                
                async function togglePartition() {
                    isPartitioned = !isPartitioned;
                    log(`${isPartitioned ? 'Enabling' : 'Disabling'} network partition on Replica 3...`);
                    
                    await fetch(`/api/partition?enable=${isPartitioned}`);
                    
                    const replica3 = document.getElementById('replica-3');
                    if (isPartitioned) {
                        replica3.classList.add('partitioned');
                        replica3.querySelector('span').textContent = 'PARTITIONED';
                        replica3.querySelector('span').style.color = '#ff4500';
                    } else {
                        replica3.classList.remove('partitioned');
                        replica3.querySelector('span').textContent = 'ONLINE';
                        replica3.querySelector('span').style.color = '#00ff41';
                    }
                    
                    updateMetrics();
                }
                
                async function runLoadTest() {
                    log('Starting load test: 100 concurrent writes...');
                    const promises = [];
                    
                    for (let i = 0; i < 100; i++) {
                        const level = i % 2 === 0 ? 'ONE' : 'QUORUM';
                        promises.push(fetch(`/api/write?level=${level}`));
                    }
                    
                    await Promise.all(promises);
                    log('✓ Load test complete!');
                    updateMetrics();
                }
                
                async function updateMetrics() {
                    try {
                        const response = await fetch('/api/metrics');
                        const data = await response.json();
                        const one = data.oneStats || {};
                        const quorum = data.quorumStats || {};
                        
                        // ONE stats
                        document.getElementById('one-avg').textContent = (one.avg ?? 0).toFixed(1) + 'ms';
                        document.getElementById('one-p50').textContent = (one.p50 ?? 0) + 'ms';
                        document.getElementById('one-p99').textContent = (one.p99 ?? 0) + 'ms';
                        document.getElementById('one-max').textContent = (one.max ?? 0).toFixed(0) + 'ms';
                        document.getElementById('one-bar').style.width = Math.min((one.max ?? 0) / 50 * 100, 100) + '%';
                        
                        // QUORUM stats
                        document.getElementById('quorum-avg').textContent = (quorum.avg ?? 0).toFixed(1) + 'ms';
                        document.getElementById('quorum-p50').textContent = (quorum.p50 ?? 0) + 'ms';
                        document.getElementById('quorum-p99').textContent = (quorum.p99 ?? 0) + 'ms';
                        document.getElementById('quorum-max').textContent = (quorum.max ?? 0).toFixed(0) + 'ms';
                        document.getElementById('quorum-bar').style.width = Math.min((quorum.max ?? 0) / 50 * 100, 100) + '%';
                        
                        // Cluster health
                        document.getElementById('total-writes').textContent = data.totalWrites ?? 0;
                        document.getElementById('failed-writes').textContent = data.failedWrites ?? 0;
                        document.getElementById('stale-reads').textContent = data.staleReads ?? 0;
                    } catch (e) {
                        console.error('Metrics fetch failed:', e);
                    }
                }
                
                // Load metrics on init and refresh every 2 seconds
                updateMetrics();
                setInterval(updateMetrics, 2000);
                
                log('Simulator initialized. Cluster ready.');
            </script>
        </body>
        </html>
        """;
    }
    
    public static void main(String[] args) throws IOException {
        // Create 3-node cluster
        List<ReplicaNode> replicas = List.of(
            new ReplicaNode(1),
            new ReplicaNode(2),
            new ReplicaNode(3)
        );
        
        CoordinatorNode coordinator = new CoordinatorNode(replicas);
        MetricsCollector metrics = new MetricsCollector();
        
        SimulatorServer server = new SimulatorServer(coordinator, metrics);
        server.start();
        
        System.out.println("✅ Cluster started with 3 replicas (RF=3)");
        System.out.println("Press Ctrl+C to shutdown");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down...");
            server.stop();
            replicas.forEach(ReplicaNode::shutdown);
        }));
    }
}
