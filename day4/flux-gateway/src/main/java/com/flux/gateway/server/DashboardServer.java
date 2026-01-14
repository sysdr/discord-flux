package com.flux.gateway.server;

import com.flux.gateway.util.Metrics;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Lightweight HTTP server for live metrics dashboard.
 */
public class DashboardServer {
    
    private final HttpServer server;
    private final GatewayServer gateway;
    
    public DashboardServer(int port, GatewayServer gateway) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.gateway = gateway;
        
        server.createContext("/", this::handleDashboard);
        server.createContext("/api/metrics", this::handleMetrics);
        server.setExecutor(null); // Default executor
        
        System.out.println("Dashboard running on http://localhost:" + port);
    }
    
    public void start() {
        server.start();
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = generateDashboardHTML();
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, response.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
    
    private void handleMetrics(HttpExchange exchange) throws IOException {
        Metrics metrics = gateway.getMetrics();
        
        // Use actual connections map size for accurate count
        int activeConnections = gateway.getConnections().size();
        
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"connections\":").append(activeConnections).append(",");
        json.append("\"opcodes\":{");
        
        Map<Byte, Long> opcodes = metrics.getOpcodeDistribution();
        int i = 0;
        for (Map.Entry<Byte, Long> entry : opcodes.entrySet()) {
            if (i++ > 0) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
        }
        
        json.append("}}");
        
        byte[] response = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
    
    private String generateDashboardHTML() {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Flux Gateway Dashboard</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: 'Courier New', monospace;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    color: #fff;
                    padding: 20px;
                    min-height: 100vh;
                }
                .container {
                    max-width: 1200px;
                    margin: 0 auto;
                }
                h1 {
                    font-size: 2.5em;
                    margin-bottom: 30px;
                    text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
                }
                .metrics-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                    gap: 20px;
                    margin-bottom: 30px;
                }
                .metric-card {
                    background: rgba(255,255,255,0.1);
                    backdrop-filter: blur(10px);
                    border-radius: 15px;
                    padding: 25px;
                    box-shadow: 0 8px 32px rgba(0,0,0,0.3);
                }
                .metric-card h2 {
                    font-size: 1.2em;
                    margin-bottom: 15px;
                    opacity: 0.8;
                }
                .metric-value {
                    font-size: 3em;
                    font-weight: bold;
                    text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
                }
                .opcode-list {
                    list-style: none;
                }
                .opcode-item {
                    background: rgba(255,255,255,0.05);
                    padding: 10px;
                    margin: 5px 0;
                    border-radius: 8px;
                    display: flex;
                    justify-content: space-between;
                }
                .opcode-name {
                    color: #ffd700;
                }
                .controls {
                    display: flex;
                    gap: 15px;
                    flex-wrap: wrap;
                }
                button {
                    background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
                    color: white;
                    border: none;
                    padding: 12px 24px;
                    border-radius: 8px;
                    font-size: 1em;
                    cursor: pointer;
                    transition: transform 0.2s;
                    box-shadow: 0 4px 15px rgba(0,0,0,0.3);
                }
                button:hover {
                    transform: translateY(-2px);
                }
                button:active {
                    transform: translateY(0);
                }
                .status {
                    background: rgba(255,255,255,0.1);
                    padding: 15px;
                    border-radius: 10px;
                    margin-top: 20px;
                    font-family: monospace;
                    max-height: 200px;
                    overflow-y: auto;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>Flux Gateway Dashboard</h1>
                
                <div class="metrics-grid">
                    <div class="metric-card">
                        <h2>Active Connections</h2>
                        <div class="metric-value" id="connections">0</div>
                    </div>
                    
                    <div class="metric-card">
                        <h2>Opcode Distribution</h2>
                        <ul class="opcode-list" id="opcodes">
                            <li>No data yet...</li>
                        </ul>
                    </div>
                </div>
                
                <div class="controls">
                    <button onclick="refreshMetrics(true)">Refresh</button>
                    <button onclick="simulateLoad()">Simulate Load</button>
                </div>
                
                <div class="status" id="status">
                    Ready. Listening for connections on port 9000...
                </div>
            </div>
            
            <script>
                const opcodeNames = {
                    0: 'Dispatch',
                    1: 'Heartbeat',
                    2: 'Identify',
                    10: 'Hello',
                    11: 'HeartbeatAck'
                };
                
                async function refreshMetrics(showStatus = false) {
                    try {
                        const response = await fetch('/api/metrics');
                        const data = await response.json();
                        
                        document.getElementById('connections').textContent = data.connections;
                        
                        const opcodeList = document.getElementById('opcodes');
                        opcodeList.innerHTML = '';
                        
                        if (Object.keys(data.opcodes).length === 0) {
                            opcodeList.innerHTML = '<li>No packets received yet</li>';
                        } else {
                            // Show total packets count if connections are 0 but opcodes exist
                            let totalPackets = 0;
                            for (const count of Object.values(data.opcodes)) {
                                totalPackets += parseInt(count);
                            }
                            if (totalPackets > 0 && data.connections === 0) {
                                opcodeList.innerHTML = '<li style="opacity: 0.7; font-style: italic;">Clients disconnected, but ' + totalPackets + ' packets were processed</li>';
                                // Still show the opcodes
                                for (const [opcode, count] of Object.entries(data.opcodes)) {
                                    const name = opcodeNames[opcode] || 'Unknown';
                                    const li = document.createElement('li');
                                    li.className = 'opcode-item';
                                    li.innerHTML = `
                                        <span class="opcode-name">${name} (${opcode})</span>
                                        <span>${count.toLocaleString()}</span>
                                    `;
                                    opcodeList.appendChild(li);
                                }
                            } else {
                                // Normal display of opcodes
                                for (const [opcode, count] of Object.entries(data.opcodes)) {
                                    const name = opcodeNames[opcode] || 'Unknown';
                                    const li = document.createElement('li');
                                    li.className = 'opcode-item';
                                    li.innerHTML = `
                                        <span class="opcode-name">${name} (${opcode})</span>
                                        <span>${count.toLocaleString()}</span>
                                    `;
                                    opcodeList.appendChild(li);
                                }
                            }
                        }
                        
                        if (showStatus) {
                            addStatus('Metrics refreshed');
                        }
                    } catch (error) {
                        addStatus('Failed to fetch metrics: ' + error.message);
                    }
                }
                
                async function simulateLoad() {
                    addStatus('To simulate load, run: ./demo.sh');
                }
                
                function clearMetrics() {
                    addStatus('Metrics reset requires server restart');
                }
                
                function addStatus(message) {
                    const status = document.getElementById('status');
                    const time = new Date().toLocaleTimeString();
                    const entry = `[${time}] ${message}<br>`;
                    status.innerHTML = entry + status.innerHTML;
                    // Limit status log to 20 entries
                    const lines = status.innerHTML.split('<br>').filter(l => l.trim());
                    if (lines.length > 20) {
                        status.innerHTML = lines.slice(0, 20).join('<br>') + '<br>';
                    }
                }
                
                // Auto-refresh every 2 seconds (silent)
                setInterval(() => refreshMetrics(false), 2000);
                refreshMetrics(false);
            </script>
        </body>
        </html>
        """;
    }
}
