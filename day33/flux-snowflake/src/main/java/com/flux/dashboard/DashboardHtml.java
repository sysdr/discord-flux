package com.flux.dashboard;

/**
 * HTML dashboard for real-time Snowflake metrics visualization.
 */
public class DashboardHtml {
    
    public static String getHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Snowflake ID Generator - Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Courier New', monospace;
            background: #0a0e27;
            color: #00ff88;
            padding: 20px;
        }
        .header {
            text-align: center;
            margin-bottom: 30px;
            border-bottom: 2px solid #00ff88;
            padding-bottom: 20px;
        }
        h1 { font-size: 2.5em; text-shadow: 0 0 10px #00ff88; }
        .subtitle { color: #888; margin-top: 10px; }
        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .card {
            background: #1a1f3a;
            border: 2px solid #00ff88;
            border-radius: 8px;
            padding: 20px;
            box-shadow: 0 0 20px rgba(0,255,136,0.3);
        }
        .card h2 {
            font-size: 1.2em;
            margin-bottom: 15px;
            color: #00ccff;
        }
        .metric {
            font-size: 2.5em;
            font-weight: bold;
            color: #00ff88;
            text-align: center;
            margin: 10px 0;
        }
        .label {
            color: #888;
            font-size: 0.9em;
            text-align: center;
        }
        .controls {
            display: flex;
            gap: 10px;
            justify-content: center;
            margin-top: 20px;
        }
        button {
            background: #00ff88;
            color: #0a0e27;
            border: none;
            padding: 12px 24px;
            font-size: 1em;
            font-weight: bold;
            cursor: pointer;
            border-radius: 4px;
            font-family: 'Courier New', monospace;
            transition: all 0.3s;
        }
        button:hover {
            background: #00ccff;
            box-shadow: 0 0 15px rgba(0,255,136,0.5);
        }
        button:active {
            transform: scale(0.95);
        }
        .histogram {
            height: 100px;
            display: flex;
            align-items: flex-end;
            gap: 2px;
            margin-top: 10px;
        }
        .bar {
            flex: 1;
            background: linear-gradient(to top, #00ff88, #00ccff);
            min-height: 2px;
            border-radius: 2px 2px 0 0;
        }
        .status {
            padding: 5px 10px;
            border-radius: 4px;
            display: inline-block;
            margin-top: 10px;
        }
        .status.ok { background: rgba(0,255,136,0.2); }
        .status.warning { background: rgba(255,200,0,0.2); color: #ffc800; }
        #console {
            background: #000;
            color: #0f0;
            padding: 15px;
            border-radius: 8px;
            font-family: 'Courier New', monospace;
            height: 200px;
            overflow-y: auto;
            border: 1px solid #00ff88;
        }
        .console-line {
            margin: 2px 0;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>⚡ SNOWFLAKE ID GENERATOR</h1>
        <div class="subtitle">Lock-Free Distributed ID Generation - Worker #1</div>
    </div>

    <div class="grid">
        <div class="card">
            <h2>📊 Total IDs Generated</h2>
            <div class="metric" id="totalIds">0</div>
            <div class="label">since server start</div>
        </div>

        <div class="card">
            <h2>🚀 Throughput</h2>
            <div class="metric" id="throughput">0</div>
            <div class="label">IDs per second</div>
        </div>

        <div class="card">
            <h2>⏱️ Average Latency</h2>
            <div class="metric" id="avgLatency">0</div>
            <div class="label">nanoseconds</div>
        </div>

        <div class="card">
            <h2>📈 P99 Latency</h2>
            <div class="metric" id="p99Latency">0</div>
            <div class="label">nanoseconds</div>
        </div>

        <div class="card">
            <h2>🕐 Clock Drift Events</h2>
            <div class="metric" id="clockDrift">0</div>
            <div class="status ok" id="clockStatus">STABLE</div>
        </div>

        <div class="card">
            <h2>⚠️ Sequence Exhaustion</h2>
            <div class="metric" id="seqExhaust">0</div>
            <div class="label">4096 IDs/ms limit hit</div>
        </div>
    </div>

    <div class="card">
        <h2>🎮 Controls</h2>
        <div class="controls">
            <button onclick="generateBatch()">Generate 1000 IDs</button>
            <button onclick="stressTest()">Stress Test (10K)</button>
            <button onclick="clearConsole()">Clear Console</button>
        </div>
    </div>

    <div class="card" style="margin-top: 20px;">
        <h2>💻 Console Output</h2>
        <div id="console"></div>
    </div>

    <script>
        let updateInterval;
        
        function log(message) {
            const console = document.getElementById('console');
            const line = document.createElement('div');
            line.className = 'console-line';
            const timestamp = new Date().toLocaleTimeString();
            line.textContent = `[${timestamp}] ${message}`;
            console.appendChild(line);
            console.scrollTop = console.scrollHeight;
        }

        async function updateMetrics() {
            try {
                const response = await fetch('/api/metrics');
                const metrics = await response.json();
                
                document.getElementById('totalIds').textContent = 
                    metrics.ids_generated.toLocaleString();
                document.getElementById('throughput').textContent = 
                    Math.floor(metrics.throughput_per_sec).toLocaleString();
                document.getElementById('avgLatency').textContent = 
                    Math.floor(metrics.avg_latency_ns).toLocaleString();
                document.getElementById('p99Latency').textContent = 
                    Math.floor(metrics.p99_latency_ns).toLocaleString();
                document.getElementById('clockDrift').textContent = 
                    metrics.clock_drift_events.toLocaleString();
                document.getElementById('seqExhaust').textContent = 
                    metrics.sequence_exhaustion_events.toLocaleString();
                
                // Update status
                const clockStatus = document.getElementById('clockStatus');
                if (metrics.clock_drift_events > 0) {
                    clockStatus.textContent = 'DRIFT DETECTED';
                    clockStatus.className = 'status warning';
                } else {
                    clockStatus.textContent = 'STABLE';
                    clockStatus.className = 'status ok';
                }
            } catch (error) {
                log('❌ Error fetching metrics: ' + error.message);
            }
        }

        async function generateBatch() {
            log('🔄 Generating 1000 IDs...');
            const start = performance.now();
            
            const promises = [];
            for (let i = 0; i < 1000; i++) {
                promises.push(fetch('/api/id'));
            }
            
            await Promise.all(promises);
            const elapsed = performance.now() - start;
            
            log(`✅ Generated 1000 IDs in ${elapsed.toFixed(2)}ms`);
            await updateMetrics();
        }

        async function stressTest() {
            log('⚡ Starting stress test: 10,000 concurrent requests...');
            const start = performance.now();
            
            const promises = [];
            for (let i = 0; i < 10000; i++) {
                promises.push(fetch('/api/id'));
            }
            
            await Promise.all(promises);
            const elapsed = performance.now() - start;
            const throughput = 10000 / (elapsed / 1000);
            
            log(`✅ Completed 10,000 IDs in ${elapsed.toFixed(2)}ms`);
            log(`📊 Throughput: ${Math.floor(throughput).toLocaleString()} IDs/sec`);
            await updateMetrics();
        }

        function clearConsole() {
            document.getElementById('console').innerHTML = '';
            log('Console cleared');
        }

        // Auto-update every 1 second
        updateInterval = setInterval(updateMetrics, 1000);
        
        // Initial load
        updateMetrics();
        log('🚀 Dashboard initialized');
        log('📡 Metrics updating every 1 second');
    </script>
</body>
</html>
        """;
    }
}
