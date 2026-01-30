package com.flux.ringbuffer;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Real-time dashboard showing buffer utilization and metrics.
 */
public class Dashboard {
    private final HttpServer server;
    private final Gateway gateway;
    
    public Dashboard(Gateway gateway, int port) throws IOException {
        this.gateway = gateway;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/", exchange -> {
            String html = generateDashboardHTML();
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        server.createContext("/metrics", exchange -> {
            String json = generateMetricsJSON();
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
    }
    
    public void start() {
        server.start();
        System.out.println("Dashboard available at http://localhost:" + server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
    }
    
    private String generateDashboardHTML() {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Flux Ring Buffer Dashboard</title>
            <style>
                body {
                    font-family: 'Monaco', 'Courier New', monospace;
                    background: #0a0a0a;
                    color: #00ff00;
                    margin: 0;
                    padding: 20px;
                }
                .header {
                    border-bottom: 2px solid #00ff00;
                    padding-bottom: 10px;
                    margin-bottom: 20px;
                }
                .metrics {
                    display: grid;
                    grid-template-columns: repeat(5, 1fr);
                    gap: 10px;
                    margin-bottom: 20px;
                }
                .metric {
                    background: #1a1a1a;
                    border: 1px solid #00ff00;
                    padding: 10px;
                }
                .metric-value {
                    font-size: 24px;
                    font-weight: bold;
                }
                .clients-grid {
                    display: grid;
                    grid-template-columns: repeat(10, 1fr);
                    gap: 5px;
                }
                .client-cell {
                    aspect-ratio: 1;
                    border: 1px solid #333;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 10px;
                    transition: all 0.3s;
                }
                .util-low { background: #003300; }
                .util-medium { background: #665500; }
                .util-high { background: #663300; }
                .util-critical { background: #660000; }
                .controls {
                    margin-top: 20px;
                    padding: 15px;
                    background: #1a1a1a;
                    border: 1px solid #00ff00;
                }
                button {
                    background: #003300;
                    color: #00ff00;
                    border: 1px solid #00ff00;
                    padding: 10px 20px;
                    margin: 5px;
                    cursor: pointer;
                    font-family: inherit;
                }
                button:hover {
                    background: #005500;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>🔥 FLUX RING BUFFER DASHBOARD</h1>
                <p>Real-time monitoring of client output buffers</p>
            </div>
            
            <div class="metrics">
                <div class="metric">
                    <div>Connected Clients</div>
                    <div class="metric-value" id="client-count">0</div>
                </div>
                <div class="metric">
                    <div>Events Processed</div>
                    <div class="metric-value" id="events-processed">0</div>
                </div>
                <div class="metric">
                    <div>Avg Buffer Util %</div>
                    <div class="metric-value" id="avg-util">0</div>
                </div>
                <div class="metric">
                    <div>Backpressure Events</div>
                    <div class="metric-value" id="backpressure">0</div>
                </div>
                <div class="metric">
                    <div>Dropped Messages</div>
                    <div class="metric-value" id="dropped">0</div>
                </div>
            </div>
            
            <h3>Client Buffer Utilization Grid</h3>
            <div class="clients-grid" id="clients-grid"></div>
            
            <div class="controls">
                <h3>Simulation Controls</h3>
                <p>Note: In this demo, controls are for visualization only. Use demo.sh for actual scenarios.</p>
            </div>
            
            <script>
                function updateDashboard() {
                    fetch('/metrics')
                        .then(r => r.json())
                        .then(data => {
                            document.getElementById('client-count').textContent = data.clientCount;
                            document.getElementById('events-processed').textContent = data.eventsProcessed;
                            document.getElementById('avg-util').textContent = data.avgUtilization + '%';
                            document.getElementById('backpressure').textContent = data.totalBackpressure;
                            document.getElementById('dropped').textContent = data.totalDropped != null ? data.totalDropped : 0;
                            
                            const grid = document.getElementById('clients-grid');
                            grid.innerHTML = '';
                            
                            data.clients.forEach(client => {
                                const cell = document.createElement('div');
                                cell.className = 'client-cell';
                                cell.textContent = client.utilization + '%';
                                
                                if (client.utilization < 50) cell.classList.add('util-low');
                                else if (client.utilization < 75) cell.classList.add('util-medium');
                                else if (client.utilization < 90) cell.classList.add('util-high');
                                else cell.classList.add('util-critical');
                                
                                cell.title = 'Client ' + client.id + ': ' + client.utilization + '%';
                                grid.appendChild(cell);
                            });
                        });
                }
                
                setInterval(updateDashboard, 500);
                updateDashboard();
            </script>
        </body>
        </html>
        """;
    }
    
    private String generateMetricsJSON() {
        var clients = gateway.getClients();
        int totalBackpressure = 0;
        int totalUtilization = 0;
        long totalDropped = 0;
        
        StringBuilder clientsJson = new StringBuilder("[");
        for (int i = 0; i < clients.size(); i++) {
            ClientConnection client = clients.get(i);
            int util = client.getBufferUtilization();
            totalUtilization += util;
            totalBackpressure += client.getBackpressureEvents();
            totalDropped += client.getDroppedMessages();
            
            if (i > 0) clientsJson.append(",");
            clientsJson.append(String.format(
                "{\"id\":\"%s\",\"utilization\":%d,\"buffered\":%d}",
                client.getClientId(), util, client.getBufferedMessages()
            ));
        }
        clientsJson.append("]");
        
        int avgUtil = clients.isEmpty() ? 0 : totalUtilization / clients.size();
        
        return String.format(
            "{\"clientCount\":%d,\"eventsProcessed\":%d,\"avgUtilization\":%d,\"totalBackpressure\":%d,\"totalDropped\":%d,\"clients\":%s}",
            clients.size(), gateway.getEventsProcessed(), avgUtil, totalBackpressure, totalDropped, clientsJson
        );
    }
}
