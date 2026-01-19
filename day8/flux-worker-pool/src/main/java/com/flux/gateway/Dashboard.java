package com.flux.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight HTTP dashboard using JDK's built-in HttpServer.
 * Serves real-time metrics and control buttons.
 */
public class Dashboard {
    private static final int PORT = 9090;
    private final WorkerPool workerPool;

    public Dashboard(WorkerPool workerPool) {
        this.workerPool = workerPool;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        server.createContext("/", exchange -> {
            try {
                String html = generateHTML();
                byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                // Prevent browser caching
                exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                exchange.getResponseHeaders().set("Pragma", "no-cache");
                exchange.getResponseHeaders().set("Expires", "0");
                exchange.sendResponseHeaders(200, htmlBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(htmlBytes);
                os.close();
            } catch (Exception e) {
                System.err.println("Error serving dashboard: " + e.getMessage());
                e.printStackTrace();
            }
        });

        server.createContext("/metrics", exchange -> {
            try {
                String json = String.format("""
                    {
                        "queueDepth": %d,
                        "processed": %d,
                        "rejected": %d,
                        "p50Latency": %d,
                        "p99Latency": %d,
                        "timestamp": %d
                    }
                    """,
                    workerPool.getQueueDepth(),
                    workerPool.getProcessedCount(),
                    workerPool.getRejectedCount(),
                    workerPool.getP50Latency(),
                    workerPool.getP99Latency(),
                    System.currentTimeMillis()
                );
                byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                // Prevent browser caching of metrics
                exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                exchange.getResponseHeaders().set("Pragma", "no-cache");
                exchange.getResponseHeaders().set("Expires", "0");
                exchange.sendResponseHeaders(200, jsonBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(jsonBytes);
                os.close();
            } catch (Exception e) {
                System.err.println("Error serving metrics: " + e.getMessage());
                e.printStackTrace();
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("📊 Dashboard running at http://localhost:" + PORT);
    }

    private String generateHTML() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Flux Worker Pool Dashboard</title>
                <style>
                    body {
                        font-family: 'Courier New', monospace;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 40px;
                        margin: 0;
                    }
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                        background: rgba(0,0,0,0.3);
                        padding: 30px;
                        border-radius: 15px;
                        box-shadow: 0 10px 40px rgba(0,0,0,0.3);
                    }
                    h1 {
                        text-align: center;
                        font-size: 2.5em;
                        margin-bottom: 40px;
                        text-shadow: 2px 2px 4px rgba(0,0,0,0.5);
                    }
                    .metrics {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                        gap: 20px;
                        margin-bottom: 30px;
                    }
                    .metric-card {
                        background: rgba(255,255,255,0.1);
                        padding: 20px;
                        border-radius: 10px;
                        text-align: center;
                        backdrop-filter: blur(10px);
                    }
                    .metric-value {
                        font-size: 3em;
                        font-weight: bold;
                        color: #4ade80;
                    }
                    .metric-label {
                        font-size: 0.9em;
                        opacity: 0.8;
                        margin-top: 10px;
                    }
                    .queue-viz {
                        background: rgba(255,255,255,0.1);
                        padding: 20px;
                        border-radius: 10px;
                        margin-top: 20px;
                    }
                    .queue-bar {
                        height: 40px;
                        background: linear-gradient(90deg, #4ade80, #fbbf24, #ef4444);
                        border-radius: 5px;
                        position: relative;
                        overflow: hidden;
                    }
                    .queue-fill {
                        height: 100%;
                        background: rgba(255,255,255,0.3);
                        transition: width 0.3s ease;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>⚡ Flux Worker Pool Monitor</h1>
                    
                    <div class="metrics">
                        <div class="metric-card">
                            <div class="metric-value" id="queueDepth">0</div>
                            <div class="metric-label">Queue Depth</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value" id="processed">0</div>
                            <div class="metric-label">Processed Tasks</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value" id="rejected">0</div>
                            <div class="metric-label">Rejected Tasks</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value" id="p50">0ms</div>
                            <div class="metric-label">p50 Latency</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value" id="p99">0ms</div>
                            <div class="metric-label">p99 Latency</div>
                        </div>
                    </div>
                    
                    <div class="queue-viz">
                        <h3>Queue Utilization (Capacity: 10,000)</h3>
                        <div class="queue-bar">
                            <div class="queue-fill" id="queueBar" style="width: 0%"></div>
                        </div>
                    </div>
                    
                    <div style="text-align: center; margin-top: 20px; opacity: 0.7; font-size: 0.9em;">
                        Last updated: <span id="lastUpdate">Never</span>
                    </div>
                    
                    <div id="statusMessage" style="text-align: center; margin-top: 15px; padding: 15px; background: rgba(255,255,255,0.1); border-radius: 8px; display: none;">
                        <p style="margin: 5px 0;">💡 <strong>No activity detected</strong></p>
                        <p style="margin: 5px 0; font-size: 0.85em;">Run <code>./demo.sh</code> or <code>./generate-traffic.sh</code> to generate test traffic</p>
                    </div>
                    
                    <div style="text-align: center; margin-top: 20px; padding: 15px; background: rgba(76, 175, 80, 0.2); border-radius: 8px; font-size: 0.9em;">
                        <p style="margin: 5px 0;">✅ <strong>System Health</strong></p>
                        <p style="margin: 5px 0; font-size: 0.85em;">
                            Queue Depth = 0 means all tasks are processed immediately (healthy!)<br>
                            Rejected Tasks = 0 means queue never filled (excellent performance!)
                        </p>
                        <p style="margin: 5px 0; font-size: 0.85em; opacity: 0.8;">
                            To test queue depth: Send "SLOW" messages (50ms each) rapidly
                        </p>
                    </div>
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
                                document.getElementById('queueDepth').textContent = data.queueDepth;
                                document.getElementById('processed').textContent = data.processed;
                                document.getElementById('rejected').textContent = data.rejected;
                                
                                // Format latency: show in ms with appropriate precision
                                // If < 1ms, show in microseconds, otherwise show in ms
                                const formatLatency = (micros) => {
                                    if (micros === 0) return '0.0ms';
                                    if (micros < 1000) {
                                        return micros + 'μs';
                                    }
                                    return (micros / 1000).toFixed(2) + 'ms';
                                };
                                
                                document.getElementById('p50').textContent = formatLatency(data.p50Latency);
                                document.getElementById('p99').textContent = formatLatency(data.p99Latency);
                                
                                const utilization = (data.queueDepth / 10000) * 100;
                                document.getElementById('queueBar').style.width = utilization + '%';
                                
                                // Update timestamp
                                if (data.timestamp) {
                                    const date = new Date(data.timestamp);
                                    document.getElementById('lastUpdate').textContent = date.toLocaleTimeString();
                                }
                                
                                // Show/hide status message based on activity
                                const statusMsg = document.getElementById('statusMessage');
                                if (data.processed === 0 && data.queueDepth === 0) {
                                    statusMsg.style.display = 'block';
                                } else {
                                    statusMsg.style.display = 'none';
                                }
                            })
                            .catch(error => {
                                console.error('Error fetching metrics:', error);
                            });
                    }
                    
                    // Update immediately on load
                    updateMetrics();
                    // Then update every 500ms
                    setInterval(updateMetrics, 500);
                </script>
            </body>
            </html>
            """;
    }
}
