package com.flux.tombstone;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class DashboardServer {
    
    private final MessageStore store;
    private final HttpServer server;
    private final Random random = new Random();
    
    public DashboardServer(MessageStore store, int port) throws IOException {
        this.store = store;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        
        server.createContext("/", this::handleDashboard);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/insert", this::handleInsert);
        server.createContext("/api/delete", this::handleDelete);
        server.createContext("/api/compact", this::handleCompact);
    }
    
    public void start() {
        server.start();
        System.out.println("📊 Dashboard: http://localhost:" + server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Flux Tombstone Dashboard</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { 
                    font-family: 'SF Mono', Monaco, monospace; 
                    background: #0a0a0a; 
                    color: #e0e0e0; 
                    padding: 20px;
                }
                .header { 
                    background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                    padding: 30px;
                    border-radius: 12px;
                    margin-bottom: 30px;
                }
                .header h1 { color: white; font-size: 32px; margin-bottom: 10px; }
                .header p { color: rgba(255,255,255,0.8); font-size: 14px; }
                
                .stats-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 20px;
                    margin-bottom: 30px;
                }
                .stat-card {
                    background: #1a1a1a;
                    padding: 20px;
                    border-radius: 8px;
                    border: 1px solid #333;
                }
                .stat-card h3 { color: #888; font-size: 12px; text-transform: uppercase; margin-bottom: 10px; }
                .stat-card .value { font-size: 36px; font-weight: bold; color: #667eea; }
                
                .controls {
                    background: #1a1a1a;
                    padding: 25px;
                    border-radius: 8px;
                    border: 1px solid #333;
                    margin-bottom: 30px;
                }
                .controls h2 { margin-bottom: 20px; color: #e0e0e0; }
                .btn-group { display: flex; gap: 15px; flex-wrap: wrap; }
                button {
                    background: #667eea;
                    color: white;
                    border: none;
                    padding: 12px 24px;
                    border-radius: 6px;
                    cursor: pointer;
                    font-size: 14px;
                    font-family: inherit;
                    transition: all 0.2s;
                }
                button:hover { background: #5568d3; transform: translateY(-2px); }
                button:active { transform: translateY(0); }
                button.danger { background: #e74c3c; }
                button.danger:hover { background: #c0392b; }
                button.success { background: #27ae60; }
                button.success:hover { background: #229954; }
                
                .log {
                    background: #1a1a1a;
                    padding: 20px;
                    border-radius: 8px;
                    border: 1px solid #333;
                    max-height: 300px;
                    overflow-y: auto;
                    font-size: 12px;
                    line-height: 1.6;
                }
                .log-entry { margin-bottom: 5px; color: #888; }
                .log-entry.info { color: #3498db; }
                .log-entry.success { color: #27ae60; }
                .log-entry.warning { color: #f39c12; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>⚰️ Tombstone Deletion System</h1>
                <p>Day 40: Understanding LSM Trees and Why We Don't DELETE Immediately</p>
            </div>
            
            <div class="stats-grid">
                <div class="stat-card">
                    <h3>Active Messages</h3>
                    <div class="value" id="active-messages">0</div>
                </div>
                <div class="stat-card">
                    <h3>Tombstones</h3>
                    <div class="value" id="tombstones" style="color: #e74c3c;">0</div>
                </div>
                <div class="stat-card">
                    <h3>SSTable Count</h3>
                    <div class="value" id="sstable-count" style="color: #f39c12;">0</div>
                </div>
                <div class="stat-card">
                    <h3>Total Inserts</h3>
                    <div class="value" id="total-inserts" style="color: #27ae60;">0</div>
                </div>
                <div class="stat-card">
                    <h3>Total Deletes</h3>
                    <div class="value" id="total-deletes" style="color: #9b59b6;">0</div>
                </div>
            </div>
            
            <div class="controls">
                <h2>Operations</h2>
                <div class="btn-group">
                    <button onclick="insertMessages(100)" class="success">Insert 100 Messages</button>
                    <button onclick="insertMessages(1000)" class="success">Insert 1000 Messages</button>
                    <button onclick="deleteRandom(50)" class="danger">Delete 50 Random</button>
                    <button onclick="deleteRandom(500)" class="danger">Delete 500 Random</button>
                    <button onclick="forceCompaction()">Force Compaction</button>
                </div>
            </div>
            
            <div class="log" id="log">
                <div class="log-entry info">[SYSTEM] Dashboard initialized</div>
            </div>
            
            <script>
                function log(message, type = 'info') {
                    const logDiv = document.getElementById('log');
                    const entry = document.createElement('div');
                    entry.className = 'log-entry ' + type;
                    entry.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;
                    logDiv.insertBefore(entry, logDiv.firstChild);
                }
                
                async function fetchStats() {
                    try {
                        const res = await fetch('/api/stats');
                        const stats = await res.json();
                        document.getElementById('active-messages').textContent = stats.activeMessages;
                        document.getElementById('tombstones').textContent = stats.tombstones;
                        document.getElementById('sstable-count').textContent = stats.sstableCount;
                        document.getElementById('total-inserts').textContent = stats.totalInserts;
                        document.getElementById('total-deletes').textContent = stats.totalDeletes;
                    } catch (e) {
                        log('Failed to fetch stats: ' + e.message, 'warning');
                    }
                }
                
                async function insertMessages(count) {
                    log(`Inserting ${count} messages...`, 'info');
                    const res = await fetch(`/api/insert?count=${count}`, { method: 'POST' });
                    const result = await res.text();
                    log(result, 'success');
                    await fetchStats();
                }
                
                async function deleteRandom(count) {
                    log(`Deleting ${count} random messages...`, 'info');
                    const res = await fetch(`/api/delete?count=${count}`, { method: 'POST' });
                    const result = await res.text();
                    log(result, 'warning');
                    await fetchStats();
                }
                
                async function forceCompaction() {
                    log('Triggering compaction...', 'info');
                    const res = await fetch('/api/compact', { method: 'POST' });
                    const result = await res.text();
                    log(result, 'success');
                    await fetchStats();
                }
                
                // Auto-refresh stats
                setInterval(fetchStats, 2000);
                fetchStats();
            </script>
        </body>
        </html>
        """;
        
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    
    private void handleStats(HttpExchange exchange) throws IOException {
        var stats = store.getStats();
        String json = String.format(
            "{\"activeMessages\":%d,\"tombstones\":%d,\"sstableCount\":%d,\"totalInserts\":%d,\"totalDeletes\":%d}",
            stats.activeMessages(), stats.tombstones(), stats.sstableCount(),
            stats.totalInserts(), stats.totalDeletes()
        );
        
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    
    private void handleInsert(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        int count = parseCount(query, 100);
        
        for (int i = 0; i < count; i++) {
            Message msg = new Message("channel-" + random.nextInt(10), 
                "Message content " + random.nextInt(10000));
            store.insert(msg);
        }
        
        String response = String.format("Inserted %d messages", count);
        sendTextResponse(exchange, response);
    }
    
    private void handleDelete(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        int count = parseCount(query, 50);
        
        var stats = store.getStats();
        if (stats.activeMessages() == 0) {
            sendTextResponse(exchange, "No messages to delete");
            return;
        }
        
        // Get actual message IDs from store (scan channels for messages)
        List<MessageId> toDelete = new ArrayList<>();
        for (int ch = 0; ch < 10 && toDelete.size() < count; ch++) {
            List<Message> msgs = store.scan("channel-" + ch, count - toDelete.size());
            for (Message m : msgs) {
                toDelete.add(m.id());
                if (toDelete.size() >= count) break;
            }
        }
        // Also check "general" channel (pre-populated)
        if (toDelete.size() < count) {
            List<Message> general = store.scan("general", count - toDelete.size());
            for (Message m : general) {
                toDelete.add(m.id());
                if (toDelete.size() >= count) break;
            }
        }
        int deleteCount = Math.min(count, toDelete.size());
        toDelete.subList(0, deleteCount).forEach(store::delete);
        
        String response = String.format("Deleted %d messages (tombstones created)", deleteCount);
        sendTextResponse(exchange, response);
    }
    
    private void handleCompact(HttpExchange exchange) throws IOException {
        store.forceCompaction();
        sendTextResponse(exchange, "Compaction completed");
    }
    
    private int parseCount(String query, int defaultValue) {
        if (query == null) return defaultValue;
        for (String param : query.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2 && kv[0].equals("count")) {
                try {
                    return Integer.parseInt(kv[1]);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }
    
    private void sendTextResponse(HttpExchange exchange, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
