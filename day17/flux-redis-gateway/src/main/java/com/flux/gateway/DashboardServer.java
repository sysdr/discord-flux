package com.flux.gateway;

import com.google.gson.Gson;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP server for real-time dashboard showing gateway metrics.
 */
public class DashboardServer {
    private final WebSocketGateway gateway;
    private final HttpServer server;
    private final Gson gson = new Gson();
    private volatile boolean demoMode = true; // Enable demo mode by default
    private long demoStartTime = System.currentTimeMillis();
    
    public DashboardServer(WebSocketGateway gateway, int port) throws IOException {
        this.gateway = gateway;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/", this::handleDashboard);
        server.createContext("/metrics", this::handleMetrics);
        server.createContext("/demo", this::handleDemoToggle);
        server.setExecutor(null);
    }
    
    public void start() {
        server.start();
        System.out.println("📊 Dashboard available at http://localhost:" + server.getAddress().getPort());
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Flux Gateway Dashboard</title>
            <style>
                body { font-family: monospace; background: #1a1a1a; color: #0f0; padding: 20px; }
                .metric { background: #000; border: 1px solid #0f0; padding: 15px; margin: 10px 0; }
                .metric h3 { margin: 0 0 10px 0; color: #0ff; }
                .connection { background: #111; padding: 10px; margin: 5px 0; border-left: 3px solid #0f0; }
                .buffer-bar { background: #333; height: 20px; position: relative; }
                .buffer-fill { background: #0f0; height: 100%; transition: width 0.3s; }
                .warn { border-left-color: #ff0; }
                .danger { border-left-color: #f00; }
            </style>
        </head>
        <body>
            <h1>Flux Redis Gateway</h1>
            
            <div class="metric">
                <h3>System Status</h3>
                <div>Connections: <span id="conn-count">0</span></div>
                <div>Active Guilds: <span id="guild-count">0</span></div>
                <div style="margin-top: 10px;">
                    <button onclick="toggleDemo()" id="demo-btn" style="background: #0f0; color: #000; border: none; padding: 5px 10px; cursor: pointer; font-weight: bold;">Enable Demo Data</button>
                    <span id="demo-status" style="margin-left: 10px; color: #666;"></span>
                </div>
            </div>
            
            <div class="metric">
                <h3>Active Connections</h3>
                <div id="connections"></div>
            </div>
            
            <script>
                function updateMetrics() {
                    fetch('/metrics')
                        .then(r => {
                            if (!r.ok) {
                                throw new Error('HTTP error! status: ' + r.status);
                            }
                            return r.json();
                        })
                        .then(data => {
                            document.getElementById('conn-count').textContent = data.connections || 0;
                            document.getElementById('guild-count').textContent = data.guilds || 0;
                            
                            const connDiv = document.getElementById('connections');
                            connDiv.innerHTML = '';
                            
                            if (data.connectionDetails && data.connectionDetails.length > 0) {
                                data.connectionDetails.forEach(conn => {
                                    const util = Math.round((conn.bufferUtil || 0) * 100);
                                    const className = util > 90 ? 'danger' : util > 70 ? 'warn' : '';
                                    
                                    connDiv.innerHTML += `
                                        <div class="connection ${className}">
                                            <div><strong>${conn.id}</strong> → Guild ${conn.guild}</div>
                                            <div>Messages Sent: ${conn.messagesSent || 0}</div>
                                            <div>Ring Buffer: ${util}%</div>
                                            <div class="buffer-bar">
                                                <div class="buffer-fill" style="width: ${util}%"></div>
                                            </div>
                                        </div>
                                    `;
                                });
                            } else {
                                connDiv.innerHTML = '<div style="color: #666; padding: 10px;">No active connections</div>';
                            }
                        })
                        .catch(error => {
                            console.error('Error fetching metrics:', error);
                            document.getElementById('conn-count').textContent = 'Error';
                            document.getElementById('guild-count').textContent = 'Error';
                        });
                }
                
                function toggleDemo() {
                    const btn = document.getElementById('demo-btn');
                    const status = document.getElementById('demo-status');
                    const isEnabled = btn.textContent.includes('Disable');
                    
                    fetch('/demo', {
                        method: isEnabled ? 'DELETE' : 'POST'
                    })
                    .then(() => {
                        btn.textContent = isEnabled ? 'Enable Demo Data' : 'Disable Demo Data';
                        status.textContent = isEnabled ? '' : 'Demo mode active';
                        updateMetrics();
                    })
                    .catch(err => console.error('Error toggling demo:', err));
                }
                
                // Check demo status on load
                fetch('/demo')
                    .then(r => r.text())
                    .then(text => {
                        if (text.includes('ON')) {
                            document.getElementById('demo-btn').textContent = 'Disable Demo Data';
                            document.getElementById('demo-status').textContent = 'Demo mode active';
                        }
                    });
                
                // Update immediately and then every second
                updateMetrics();
                setInterval(updateMetrics, 1000);
            </script>
        </body>
        </html>
        """;
        
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
    
    private void handleMetrics(HttpExchange exchange) throws IOException {
        Map<String, Object> metrics = gateway.getMetrics();
        
        // If no real connections and demo mode is enabled, generate demo data
        if (demoMode && (metrics.get("connections").equals(0) || 
            ((List<?>) metrics.get("connectionDetails")).isEmpty())) {
            metrics = generateDemoMetrics();
        }
        
        String json = gson.toJson(metrics);
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
    
    private void handleDemoToggle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("POST".equals(method)) {
            demoMode = true;
            demoStartTime = System.currentTimeMillis();
            String response = "Demo mode enabled";
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
        } else if ("DELETE".equals(method)) {
            demoMode = false;
            String response = "Demo mode disabled";
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
        } else {
            String response = demoMode ? "Demo mode: ON" : "Demo mode: OFF";
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
        }
        exchange.close();
    }
    
    private Map<String, Object> generateDemoMetrics() {
        Map<String, Object> metrics = new java.util.HashMap<>();
        long elapsed = (System.currentTimeMillis() - demoStartTime) / 1000;
        
        // Simulate 3-5 guilds
        int numGuilds = 3 + (int)(elapsed % 3);
        int totalConnections = 0;
        List<Map<String, Object>> connDetails = new java.util.ArrayList<>();
        
        String[] guildIds = {"guild-alpha", "guild-beta", "guild-gamma", "guild-delta", "guild-epsilon"};
        
        for (int g = 0; g < numGuilds; g++) {
            String guildId = guildIds[g % guildIds.length];
            // 5-15 connections per guild
            int connsPerGuild = 5 + (int)((elapsed + g) % 11);
            totalConnections += connsPerGuild;
            
            for (int c = 0; c < connsPerGuild; c++) {
                Map<String, Object> conn = new java.util.HashMap<>();
                conn.put("id", "demo-conn-" + g + "-" + c);
                conn.put("guild", guildId);
                
                // Simulate varying buffer utilization (0-85%)
                double bufferUtil = (Math.sin(elapsed * 0.1 + g + c) + 1) * 0.425;
                conn.put("bufferUtil", bufferUtil);
                
                // Simulate messages sent (increasing over time)
                int messagesSent = (int)(elapsed * 10 + g * 50 + c * 5);
                conn.put("messagesSent", messagesSent);
                
                connDetails.add(conn);
            }
        }
        
        metrics.put("connections", totalConnections);
        metrics.put("guilds", numGuilds);
        metrics.put("connectionDetails", connDetails);
        
        return metrics;
    }
    
    public void stop() {
        server.stop(0);
    }
}
