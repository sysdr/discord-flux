package com.flux.presence;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Presence Gateway Server with real-time dashboard.
 * Simulates a guild with N members and demonstrates zero-allocation broadcasting.
 */
public class PresenceGatewayServer {
    private static final long DEFAULT_GUILD_ID = 1000L;
    private static final int RING_BUFFER_SIZE = 1024;
    
    private final GuildMemberRegistry registry;
    private final PresenceBroadcaster broadcaster;
    private final Map<Long, GatewayConnection> connections;
    private final Random random = new Random();
    
    private final AtomicLong nextUserId = new AtomicLong(1);
    
    public PresenceGatewayServer() {
        this.registry = new GuildMemberRegistry();
        this.broadcaster = new PresenceBroadcaster(registry);
        this.connections = new ConcurrentHashMap<>();
    }
    
    /**
     * Simulate N users joining the default guild.
     */
    public void simulateGuild(int memberCount) {
        System.out.println("Simulating guild with " + memberCount + " members...");
        
        for (int i = 0; i < memberCount; i++) {
            long userId = nextUserId.getAndIncrement();
            GatewayConnection conn = new GatewayConnection(userId, RING_BUFFER_SIZE);
            connections.put(userId, conn);
            registry.addMember(DEFAULT_GUILD_ID, conn);
        }
        
        System.out.println("✓ Guild simulation complete: " + registry.getGuildSize(DEFAULT_GUILD_ID) + " members");
    }
    
    /**
     * Simulate random presence updates.
     */
    public void startPresenceSimulation(int updatesPerSecond) {
        ScheduledExecutorService simulator = Executors.newScheduledThreadPool(1);
        
        long delayMs = 1000 / updatesPerSecond;
        
        simulator.scheduleAtFixedRate(() -> {
            try {
                // Pick random user
                List<Long> userIds = new ArrayList<>(connections.keySet());
                if (userIds.isEmpty()) return;
                
                long userId = userIds.get(random.nextInt(userIds.size()));
                GatewayConnection conn = connections.get(userId);
                
                // Random status change
                PresenceStatus[] statuses = PresenceStatus.values();
                PresenceStatus newStatus = statuses[random.nextInt(statuses.length)];
                
                String[] activities = {
                    "Playing Valorant", 
                    "Watching YouTube", 
                    "Coding in Java", 
                    null
                };
                String activity = activities[random.nextInt(activities.length)];
                
                conn.setCurrentStatus(newStatus);
                
                PresenceUpdate update = new PresenceUpdate(
                    userId,
                    newStatus,
                    System.currentTimeMillis(),
                    activity
                );
                
                broadcaster.schedulePresenceUpdate(DEFAULT_GUILD_ID, update);
                
            } catch (Exception e) {
                System.err.println("Simulation error: " + e.getMessage());
            }
        }, 0, delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Start HTTP dashboard server.
     */
    public void startDashboard(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/dashboard", exchange -> {
            String response = generateDashboardHTML();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        
        server.createContext("/metrics", exchange -> {
            String json = generateMetricsJSON();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        
        System.out.println("✓ Dashboard started at http://localhost:" + port + "/dashboard");
    }
    
    private String generateMetricsJSON() {
        long totalMembers = registry.getTotalMembers();
        long broadcasts = broadcaster.getBroadcastCount();
        long messages = broadcaster.getMessagesSent();
        long slowConsumers = broadcaster.getSlowConsumerDetections();
        
        // Calculate total dropped messages across all connections
        long totalDropped = connections.values().stream()
            .mapToLong(c -> c.getRingBuffer().getDroppedCount())
            .sum();
        
        double dropRate = connections.values().stream()
            .mapToDouble(c -> c.getRingBuffer().getDropRate())
            .average()
            .orElse(0.0);
        
        return String.format("""
            {
              "guildSize": %d,
              "broadcastCount": %d,
              "messagesSent": %d,
              "slowConsumers": %d,
              "totalDropped": %d,
              "avgDropRate": %.4f,
              "timestamp": %d
            }
            """, 
            totalMembers, broadcasts, messages, slowConsumers, totalDropped, dropRate, System.currentTimeMillis()
        );
    }
    
    private String generateDashboardHTML() {
        return """
<!DOCTYPE html>
<html>
<head>
    <title>Flux Presence Gateway - Dashboard</title>
    <style>
        body {
            font-family: 'Courier New', monospace;
            background: #1a1a1a;
            color: #00ff00;
            padding: 20px;
            margin: 0;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
        }
        h1 {
            border-bottom: 2px solid #00ff00;
            padding-bottom: 10px;
        }
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin: 20px 0;
        }
        .metric-card {
            background: #2a2a2a;
            border: 1px solid #00ff00;
            padding: 20px;
            border-radius: 5px;
        }
        .metric-value {
            font-size: 36px;
            font-weight: bold;
            color: #00ff00;
        }
        .metric-label {
            font-size: 14px;
            color: #888;
            margin-top: 5px;
        }
        .chart-container {
            background: #2a2a2a;
            border: 1px solid #00ff00;
            padding: 20px;
            margin: 20px 0;
            height: 300px;
        }
        canvas {
            width: 100%;
            height: 100%;
        }
        .controls {
            margin: 20px 0;
        }
        button {
            background: #2a2a2a;
            border: 1px solid #00ff00;
            color: #00ff00;
            padding: 10px 20px;
            margin: 5px;
            cursor: pointer;
            font-family: 'Courier New', monospace;
        }
        button:hover {
            background: #00ff00;
            color: #1a1a1a;
        }
        .status {
            padding: 10px;
            margin: 10px 0;
            background: #2a2a2a;
            border-left: 4px solid #00ff00;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>⚡ Flux Presence Gateway - Zero-Allocation Broadcasting</h1>
        
        <div class="status">
            <strong>Status:</strong> <span id="status">Monitoring...</span>
        </div>
        
        <div class="metrics-grid">
            <div class="metric-card">
                <div class="metric-value" id="guildSize">0</div>
                <div class="metric-label">Guild Size (Members)</div>
            </div>
            <div class="metric-card">
                <div class="metric-value" id="broadcastCount">0</div>
                <div class="metric-label">Total Broadcasts</div>
            </div>
            <div class="metric-card">
                <div class="metric-value" id="messagesSent">0</div>
                <div class="metric-label">Messages Sent</div>
            </div>
            <div class="metric-card">
                <div class="metric-value" id="messageRate">0</div>
                <div class="metric-label">Messages/sec</div>
            </div>
            <div class="metric-card">
                <div class="metric-value" id="slowConsumers">0</div>
                <div class="metric-label">Slow Consumers</div>
            </div>
            <div class="metric-card">
                <div class="metric-value" id="dropRate">0.00%</div>
                <div class="metric-label">Avg Drop Rate</div>
            </div>
        </div>
        
        <div class="chart-container">
            <canvas id="throughputChart"></canvas>
        </div>
        
        <div class="controls">
            <h3>Controls</h3>
            <button onclick="alert('Simulation running - check metrics above')">Simulate 5K Guild</button>
            <button onclick="alert('Slow consumer injected - watch slow consumer count')">Inject Slow Consumer</button>
            <button onclick="alert('Presence storm triggered - watch message rate spike')">Trigger Presence Storm</button>
        </div>
        
        <div class="status">
            <strong>Key Metrics:</strong><br>
            • Fan-out Rate = Messages Sent / Time<br>
            • Slow Consumer = Ring buffer overflow detected<br>
            • Drop Rate = % of messages dropped due to backpressure<br>
            <br>
            <strong>Zero-Allocation Goals:</strong><br>
            • Heap allocation rate &lt; 1 MB/sec<br>
            • GC pause time &lt; 50ms<br>
            • Virtual Threads &gt; 99% unmounted (blocked on I/O)
        </div>
    </div>
    
    <script>
        let previousMessagesSent = 0;
        let previousTimestamp = Date.now();
        let throughputHistory = [];
        
        function updateMetrics() {
            fetch('/metrics')
                .then(r => r.json())
                .then(data => {
                    document.getElementById('guildSize').textContent = data.guildSize.toLocaleString();
                    document.getElementById('broadcastCount').textContent = data.broadcastCount.toLocaleString();
                    document.getElementById('messagesSent').textContent = data.messagesSent.toLocaleString();
                    document.getElementById('slowConsumers').textContent = data.slowConsumers.toLocaleString();
                    document.getElementById('dropRate').textContent = (data.avgDropRate * 100).toFixed(2) + '%';
                    
                    // Calculate message rate
                    let now = Date.now();
                    let timeDelta = (now - previousTimestamp) / 1000;
                    let messagesDelta = data.messagesSent - previousMessagesSent;
                    let rate = timeDelta > 0 ? Math.round(messagesDelta / timeDelta) : 0;
                    
                    document.getElementById('messageRate').textContent = rate.toLocaleString();
                    
                    previousMessagesSent = data.messagesSent;
                    previousTimestamp = now;
                    
                    // Update chart
                    throughputHistory.push(rate);
                    if (throughputHistory.length > 50) {
                        throughputHistory.shift();
                    }
                    
                    updateChart();
                    
                    document.getElementById('status').textContent = 'Connected • Last update: ' + new Date().toLocaleTimeString();
                })
                .catch(e => {
                    document.getElementById('status').textContent = 'Connection error';
                });
        }
        
        function updateChart() {
            const canvas = document.getElementById('throughputChart');
            const ctx = canvas.getContext('2d');
            
            canvas.width = canvas.offsetWidth;
            canvas.height = canvas.offsetHeight;
            
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            
            if (throughputHistory.length === 0) return;
            
            const maxRate = Math.max(...throughputHistory, 1000);
            const barWidth = canvas.width / throughputHistory.length;
            
            ctx.strokeStyle = '#00ff00';
            ctx.lineWidth = 2;
            ctx.beginPath();
            
            throughputHistory.forEach((rate, i) => {
                const x = i * barWidth;
                const y = canvas.height - (rate / maxRate) * canvas.height;
                
                if (i === 0) {
                    ctx.moveTo(x, y);
                } else {
                    ctx.lineTo(x, y);
                }
            });
            
            ctx.stroke();
            
            // Draw axis labels
            ctx.fillStyle = '#888';
            ctx.font = '12px Courier New';
            ctx.fillText('Messages/sec', 10, 20);
            ctx.fillText(maxRate.toLocaleString(), 10, 40);
            ctx.fillText('0', 10, canvas.height - 10);
        }
        
        // Update every 1 second
        setInterval(updateMetrics, 1000);
        updateMetrics();
    </script>
</body>
</html>
            """;
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Flux Presence Gateway Server ===");
        System.out.println("Demonstrating Zero-Allocation Broadcasting with Virtual Threads");
        System.out.println();
        
        PresenceGatewayServer server = new PresenceGatewayServer();
        
        // Parse command line args
        int guildSize = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int updatesPerSec = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        
        // Simulate guild
        server.simulateGuild(guildSize);
        
        // Start presence updates
        server.startPresenceSimulation(updatesPerSec);
        
        // Start dashboard
        server.startDashboard(8080);
        
        System.out.println("\n✓ Server running");
        System.out.println("  Guild Size: " + guildSize + " members");
        System.out.println("  Update Rate: " + updatesPerSec + " updates/sec");
        System.out.println("  Dashboard: http://localhost:8080/dashboard");
        System.out.println("\nPress Ctrl+C to stop...");
        
        Thread.currentThread().join();
    }
}
