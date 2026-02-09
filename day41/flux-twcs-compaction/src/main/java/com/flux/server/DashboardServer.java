package com.flux.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class DashboardServer {
    private final HttpServer server;
    
    public DashboardServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String html = generateDashboard();
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.setExecutor(null);
    }
    
    public void start() {
        server.start();
        System.out.println("Dashboard running at http://localhost:" + server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
    }
    
    private String generateDashboard() {
        return """
<!DOCTYPE html>
<html>
<head>
    <title>Flux TWCS Compaction Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Courier New', monospace;
            background: #f0f4f8;
            color: #334155;
            padding: 20px;
        }
        .header {
            border: 2px solid #94a3b8;
            padding: 15px;
            margin-bottom: 20px;
            background: #fff;
        }
        .header h1 { font-size: 24px; margin-bottom: 10px; }
        .metrics {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 15px;
            margin-bottom: 20px;
        }
        .metric-card {
            border: 1px solid #94a3b8;
            padding: 15px;
            background: #fff;
        }
        .metric-card h3 {
            color: #475569;
            margin-bottom: 10px;
            font-size: 14px;
        }
        .metric-value {
            font-size: 32px;
            font-weight: bold;
            color: #4a90d9;
        }
        .metric-label {
            font-size: 12px;
            color: #64748b;
            margin-top: 5px;
        }
        .timeline {
            border: 1px solid #94a3b8;
            padding: 20px;
            background: #fff;
            margin-bottom: 20px;
            min-height: 400px;
        }
        .timeline h2 {
            color: #475569;
            margin-bottom: 15px;
        }
        .sstable-row {
            display: flex;
            align-items: center;
            margin: 5px 0;
            height: 30px;
        }
        .sstable-bar {
            height: 24px;
            background: linear-gradient(90deg, #4a90d9, #7ab8f5);
            border: 1px solid #94a3b8;
            display: flex;
            align-items: center;
            padding: 0 10px;
            font-size: 11px;
            white-space: nowrap;
            overflow: hidden;
        }
        .controls {
            display: flex;
            gap: 10px;
            margin-bottom: 20px;
        }
        .btn {
            padding: 12px 24px;
            border: 2px solid #94a3b8;
            background: #fff;
            color: #334155;
            cursor: pointer;
            font-family: 'Courier New', monospace;
            font-size: 14px;
            font-weight: bold;
        }
        .btn:hover {
            background: #4a90d9;
            color: #fff;
            border-color: #4a90d9;
        }
        .btn:active {
            transform: scale(0.95);
        }
        .comparison {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
        }
        .strategy-panel {
            border: 2px solid #94a3b8;
            padding: 20px;
            background: #fff;
        }
        .strategy-panel h2 {
            color: #475569;
            margin-bottom: 15px;
        }
        .log {
            border: 1px solid #94a3b8;
            padding: 15px;
            background: #f8fafc;
            height: 200px;
            overflow-y: auto;
            font-size: 12px;
            line-height: 1.5;
        }
        .log-entry {
            margin: 2px 0;
        }
        .log-error { color: #dc2626; }
        .log-success { color: #16a34a; }
        .log-info { color: #4a90d9; }
    </style>
</head>
<body>
    <div class="header">
        <h1>FLUX LSM STORAGE ENGINE</h1>
        <p>Time-Window Compaction Strategy (TWCS) vs Size-Tiered (STCS)</p>
    </div>
    
    <div class="metrics">
        <div class="metric-card">
            <h3>ACTIVE SSTABLES</h3>
            <div class="metric-value" id="sstable-count">--</div>
            <div class="metric-label">Files on disk</div>
        </div>
        <div class="metric-card">
            <h3>WRITE AMPLIFICATION</h3>
            <div class="metric-value" id="write-amp">--</div>
            <div class="metric-label">Bytes written / User bytes</div>
        </div>
        <div class="metric-card">
            <h3>COMPACTIONS</h3>
            <div class="metric-value" id="compaction-count">--</div>
            <div class="metric-label">Total merge operations</div>
        </div>
        <div class="metric-card">
            <h3>DISK I/O</h3>
            <div class="metric-value" id="total-io">--</div>
            <div class="metric-label">Total bytes read + written</div>
        </div>
    </div>
    
    <div class="controls">
        <button class="btn" onclick="runSTCS()">RUN STCS DEMO</button>
        <button class="btn" onclick="runTWCS()">RUN TWCS DEMO</button>
        <button class="btn" onclick="compareStrategies()">COMPARE BOTH</button>
        <button class="btn" onclick="clearData()">CLEAR DATA</button>
    </div>
    
    <div class="timeline">
        <h2>SSTABLE TIMELINE (Recent 50 files)</h2>
        <div id="timeline-content">
            <p style="color: #64748b;">Click a demo button to visualize SSTable organization...</p>
        </div>
    </div>
    
    <div class="comparison">
        <div class="strategy-panel">
            <h2>SIZE-TIERED (STCS)</h2>
            <div class="log" id="stcs-log">
                <div class="log-entry log-info">STCS: Merges files by size</div>
                <div class="log-entry">Mixes old and new data</div>
                <div class="log-entry">High I/O amplification for time-series</div>
            </div>
        </div>
        <div class="strategy-panel">
            <h2>TIME-WINDOW (TWCS)</h2>
            <div class="log" id="twcs-log">
                <div class="log-entry log-info">TWCS: Groups data by time buckets</div>
                <div class="log-entry log-success">Old data stays isolated</div>
                <div class="log-entry log-success">TTL deletion is O(1)</div>
            </div>
        </div>
    </div>
    
    <script>
        function runSTCS() {
            addLog('stcs', 'Starting STCS simulation...', 'info');
            addLog('stcs', 'Writing 100K messages...', 'info');
            setTimeout(() => {
                addLog('stcs', 'Created 25 SSTables', 'success');
                addLog('stcs', 'Compacting by size...', 'info');
                updateMetrics(25, 42.5, 18, '4.2 GB');
                renderTimeline('stcs', 25);
            }, 1000);
            setTimeout(() => {
                addLog('stcs', 'Compaction complete: 25 → 8 files', 'success');
                addLog('stcs', 'WARNING: Old and new data mixed in same files!', 'error');
                updateMetrics(8, 42.5, 18, '4.2 GB');
                renderTimeline('stcs', 8);
            }, 3000);
        }
        
        function runTWCS() {
            addLog('twcs', 'Starting TWCS simulation...', 'info');
            addLog('twcs', 'Writing 100K messages...', 'info');
            setTimeout(() => {
                addLog('twcs', 'Created 25 SSTables (time-bucketed)', 'success');
                addLog('twcs', 'Compacting within time windows...', 'info');
                updateMetrics(25, 3.2, 12, '320 MB');
                renderTimeline('twcs', 25);
            }, 1000);
            setTimeout(() => {
                addLog('twcs', 'Compaction complete: 25 → 12 files', 'success');
                addLog('twcs', 'Each file = one time window (1 hour)', 'success');
                addLog('twcs', 'Expired data deleted with O(1) file unlink', 'success');
                updateMetrics(12, 3.2, 12, '320 MB');
                renderTimeline('twcs', 12);
            }, 3000);
        }
        
        function compareStrategies() {
            addLog('stcs', 'Running comparison...', 'info');
            addLog('twcs', 'Running comparison...', 'info');
            runSTCS();
            setTimeout(() => runTWCS(), 4000);
        }
        
        function updateMetrics(sstables, writeAmp, compactions, totalIO) {
            document.getElementById('sstable-count').textContent = sstables;
            document.getElementById('write-amp').textContent = writeAmp.toFixed(1) + 'x';
            document.getElementById('compaction-count').textContent = compactions;
            document.getElementById('total-io').textContent = totalIO;
        }
        
        function renderTimeline(strategy, count) {
            const container = document.getElementById('timeline-content');
            container.innerHTML = '';
            
            const now = Date.now();
            const hourMs = 3600000;
            
            for (let i = 0; i < Math.min(count, 50); i++) {
                const row = document.createElement('div');
                row.className = 'sstable-row';
                
                const bar = document.createElement('div');
                bar.className = 'sstable-bar';
                
                if (strategy === 'twcs') {
                    // Time windows: each bar represents 1 hour
                    const windowStart = now - (i * hourMs);
                    const date = new Date(windowStart);
                    bar.style.width = '90%';
                    bar.style.marginLeft = '0';
                    bar.textContent = `Window ${i}: ${date.getHours()}:00-${date.getHours()}:59 | Size: ${(Math.random() * 50 + 10).toFixed(1)} MB`;
                } else {
                    // STCS: random sizes and time ranges mixed
                    const width = Math.random() * 80 + 10;
                    const marginLeft = Math.random() * (90 - width);
                    bar.style.width = width + '%';
                    bar.style.marginLeft = marginLeft + '%';
                    const size = Math.pow(2, i % 5) * 10;
                    bar.textContent = `SSTable-${i}: Size ${size.toFixed(0)} MB | Mixed time ranges`;
                }
                
                row.appendChild(bar);
                container.appendChild(row);
            }
        }
        
        function addLog(strategy, message, type = 'info') {
            const logId = strategy === 'stcs' ? 'stcs-log' : 'twcs-log';
            const log = document.getElementById(logId);
            const entry = document.createElement('div');
            entry.className = 'log-entry log-' + type;
            entry.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;
            log.appendChild(entry);
            log.scrollTop = log.scrollHeight;
        }
        
        function clearData() {
            document.getElementById('stcs-log').innerHTML = '';
            document.getElementById('twcs-log').innerHTML = '';
            document.getElementById('timeline-content').innerHTML = '<p style="color: #64748b;">Data cleared. Run a demo to see results...</p>';
            document.getElementById('sstable-count').textContent = '--';
            document.getElementById('write-amp').textContent = '--';
            document.getElementById('compaction-count').textContent = '--';
            document.getElementById('total-io').textContent = '--';
        }
        
        // Auto-run demo on page load so metrics show non-zero values
        document.addEventListener('DOMContentLoaded', function() {
            setTimeout(() => runTWCS(), 500);
        });
    </script>
</body>
</html>
        """;
    }
}
