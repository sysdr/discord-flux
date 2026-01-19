package com.flux.netpoll;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class DashboardServer {
    private final HttpServer server;
    private final ReactorLoop reactor;

    public DashboardServer(int port, ReactorLoop reactor) throws IOException {
        // Bind to all interfaces (0.0.0.0) for WSL2 and network access
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        this.reactor = reactor;
        
        server.createContext("/dashboard", exchange -> {
            String html = generateDashboardHTML();
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.createContext("/api/stats", exchange -> {
            ReactorLoop.Stats stats = reactor.getStats();
            String json = String.format(
                "{\"wakeCount\":%d,\"eventsProcessed\":%d,\"activeConnections\":%d,\"activeThreads\":%d}",
                stats.wakeCount(), stats.eventsProcessed(), 
                stats.activeConnections(), stats.activeThreads()
            );
            
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.setExecutor(null);
    }

    public void start() {
        server.start();
        System.out.println("✓ Dashboard server started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }

    private String generateDashboardHTML() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Flux Netpoll Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            font-family: 'Courier New', monospace; 
            background: #0a0a0a; 
            color: #00ff00; 
            padding: 20px; 
        }
        .header { 
            text-align: center; 
            margin-bottom: 30px; 
            border-bottom: 2px solid #00ff00; 
            padding-bottom: 10px; 
        }
        .stats-grid { 
            display: grid; 
            grid-template-columns: repeat(4, 1fr); 
            gap: 20px; 
            margin-bottom: 30px; 
        }
        .stat-box { 
            background: #1a1a1a; 
            border: 2px solid #00ff00; 
            padding: 20px; 
            text-align: center; 
        }
        .stat-value { 
            font-size: 2em; 
            font-weight: bold; 
            margin-top: 10px; 
        }
        .connection-grid { 
            display: grid; 
            grid-template-columns: repeat(50, 1fr); 
            gap: 3px; 
            margin-top: 20px;
            padding: 10px;
            background: #0f0f0f;
            border: 1px solid #00ff00;
        }
        .connection-box { 
            width: 100%; 
            aspect-ratio: 1; 
            background: #222; 
            border: 1px solid #333;
            transition: all 0.2s ease;
            min-height: 10px;
        }
        .connection-box.active { 
            background: #00ff00; 
            border-color: #00cc00;
            box-shadow: 0 0 5px #00ff00;
        }
        .connection-box.idle { 
            background: #ffaa00; 
            border-color: #cc8800;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>&#9889; FLUX NETPOLL REACTOR &#9889;</h1>
        <p>Real-time epoll/kqueue Monitoring</p>
    </div>
    
    <div class="stats-grid">
        <div class="stat-box">
            <div>Selector Wakes</div>
            <div class="stat-value" id="wakeCount">0</div>
        </div>
        <div class="stat-box">
            <div>Events Processed</div>
            <div class="stat-value" id="eventsProcessed">0</div>
        </div>
        <div class="stat-box">
            <div>Active Connections</div>
            <div class="stat-value" id="activeConnections">0</div>
        </div>
        <div class="stat-box">
            <div>Virtual Threads</div>
            <div class="stat-value" id="activeThreads">0</div>
        </div>
    </div>
    
    <h2>Connection Grid (1000 slots) - <span id="gridStatus">Initializing...</span></h2>
    <div class="connection-grid" id="connectionGrid"></div>
    
    <script>
        // Initialize grid
        const grid = document.getElementById('connectionGrid');
        const gridStatus = document.getElementById('gridStatus');
        
        // Create 1000 connection boxes
        for (let i = 0; i < 1000; i++) {
            const box = document.createElement('div');
            box.className = 'connection-box';
            box.id = 'conn-' + i;
            box.title = 'Connection slot ' + i;
            grid.appendChild(box);
        }
        
        // Update stats every 500ms
        setInterval(async () => {
            try {
                const response = await fetch('/api/stats');
                if (!response.ok) {
                    throw new Error('Failed to fetch stats: ' + response.status);
                }
                const stats = await response.json();
                
                // Update stat values
                const wakeCount = stats.wakeCount || 0;
                const eventsProcessed = stats.eventsProcessed || 0;
                const activeConnections = stats.activeConnections || 0;
                const activeThreads = stats.activeThreads || 0;
                
                document.getElementById('wakeCount').textContent = wakeCount;
                document.getElementById('eventsProcessed').textContent = eventsProcessed;
                document.getElementById('activeConnections').textContent = activeConnections;
                document.getElementById('activeThreads').textContent = activeThreads;
                
                // Update connection grid - show active connections
                for (let i = 0; i < 1000; i++) {
                    const box = document.getElementById('conn-' + i);
                    if (box) {
                        if (i < activeConnections) {
                            box.className = 'connection-box active';
                        } else {
                            box.className = 'connection-box';
                        }
                    }
                }
                
                // Update status
                if (activeConnections > 0) {
                    gridStatus.textContent = activeConnections + ' active connections';
                    gridStatus.style.color = '#00ff00';
                } else {
                    gridStatus.textContent = 'No active connections - run a test client';
                    gridStatus.style.color = '#ffaa00';
                }
            } catch (e) {
                // Only log errors, not debug info
                console.error('Failed to fetch stats:', e);
                gridStatus.textContent = 'Error: ' + e.message;
                gridStatus.style.color = '#ff0000';
            }
        }, 500);
    </script>
</body>
</html>
""";
    }
}
