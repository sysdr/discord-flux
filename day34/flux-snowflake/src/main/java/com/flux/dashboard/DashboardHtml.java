package com.flux.dashboard;

/**
 * HTML dashboard for real-time Snowflake metrics visualization.
 * Day34: Sidebar layout with amber theme and throughput history chart.
 */
public class DashboardHtml {

    public static String getHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Flux Snowflake - Day 34</title>
    <link href="https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&display=swap" rel="stylesheet">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'DM Mono', monospace;
            background: linear-gradient(135deg, #1a0f0a 0%, #0d0a08 100%);
            color: #e8d5c4;
            min-height: 100vh;
            display: flex;
        }
        .sidebar {
            width: 280px;
            background: rgba(255, 107, 53, 0.08);
            border-right: 1px solid rgba(255, 107, 53, 0.3);
            padding: 24px 16px;
        }
        .sidebar-title {
            font-size: 1.1em;
            color: #ff6b35;
            margin-bottom: 24px;
            padding-bottom: 12px;
            border-bottom: 1px solid rgba(255, 107, 53, 0.3);
        }
        .sidebar-metric {
            padding: 12px 0;
            border-bottom: 1px solid rgba(255, 107, 53, 0.1);
        }
        .sidebar-metric:last-child { border-bottom: none; }
        .sidebar-metric .name { font-size: 0.75em; color: #8b7355; }
        .sidebar-metric .value { font-size: 1.4em; color: #ff6b35; font-weight: 500; }
        .main {
            flex: 1;
            padding: 24px;
            display: flex;
            flex-direction: column;
            gap: 24px;
        }
        .header-bar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding-bottom: 16px;
            border-bottom: 1px solid rgba(255, 107, 53, 0.2);
        }
        .header-bar h1 {
            font-size: 1.8em;
            color: #ff6b35;
            text-shadow: 0 0 20px rgba(255, 107, 53, 0.3);
        }
        .header-bar .badge {
            background: rgba(255, 107, 53, 0.2);
            color: #ff6b35;
            padding: 6px 12px;
            border-radius: 20px;
            font-size: 0.85em;
        }
        .chart-section {
            background: rgba(0, 0, 0, 0.3);
            border: 1px solid rgba(255, 107, 53, 0.2);
            border-radius: 8px;
            padding: 20px;
        }
        .chart-section h3 {
            font-size: 0.9em;
            color: #8b7355;
            margin-bottom: 12px;
        }
        .throughput-chart {
            height: 80px;
            display: flex;
            align-items: flex-end;
            gap: 3px;
        }
        .chart-bar {
            flex: 1;
            background: linear-gradient(to top, #ff6b35, #f7931e);
            min-height: 4px;
            border-radius: 3px 3px 0 0;
            opacity: 0.9;
        }
        .bottom-row {
            display: grid;
            grid-template-columns: 1fr 320px;
            gap: 24px;
            flex: 1;
            min-height: 0;
        }
        .controls-panel {
            background: rgba(0, 0, 0, 0.3);
            border: 1px solid rgba(255, 107, 53, 0.2);
            border-radius: 8px;
            padding: 20px;
        }
        .controls-panel h3 {
            font-size: 0.9em;
            color: #8b7355;
            margin-bottom: 16px;
        }
        .btn-group {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
        }
        .btn {
            background: transparent;
            color: #ff6b35;
            border: 1px solid #ff6b35;
            padding: 10px 18px;
            font-family: 'DM Mono', monospace;
            font-size: 0.9em;
            cursor: pointer;
            border-radius: 6px;
            transition: all 0.2s;
        }
        .btn:hover {
            background: rgba(255, 107, 53, 0.15);
            box-shadow: 0 0 12px rgba(255, 107, 53, 0.2);
        }
        .activity-panel {
            background: rgba(0, 0, 0, 0.5);
            border: 1px solid rgba(255, 107, 53, 0.2);
            border-radius: 8px;
            padding: 16px;
            overflow-y: auto;
        }
        .activity-panel h3 {
            font-size: 0.9em;
            color: #8b7355;
            margin-bottom: 12px;
        }
        .activity-line {
            font-size: 0.8em;
            color: #c4a574;
            padding: 4px 0;
            border-bottom: 1px solid rgba(255, 107, 53, 0.08);
        }
        .activity-line:last-child { border-bottom: none; }
        .status-dot {
            display: inline-block;
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: #22c55e;
            margin-right: 8px;
            animation: pulse 2s infinite;
        }
        .status-dot.warn { background: #eab308; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
    </style>
</head>
<body>
    <aside class="sidebar">
        <div class="sidebar-title">FLUX DAY 34</div>
        <div class="sidebar-metric">
            <div class="name">TOTAL IDs</div>
            <div class="value" id="totalIds">0</div>
        </div>
        <div class="sidebar-metric">
            <div class="name">THROUGHPUT</div>
            <div class="value" id="throughput">0 <span style="font-size:0.7em">/s</span></div>
        </div>
        <div class="sidebar-metric">
            <div class="name">AVG LATENCY</div>
            <div class="value" id="avgLatency">0 <span style="font-size:0.7em">ns</span></div>
        </div>
        <div class="sidebar-metric">
            <div class="name">P99 LATENCY</div>
            <div class="value" id="p99Latency">0 <span style="font-size:0.7em">ns</span></div>
        </div>
        <div class="sidebar-metric">
            <div class="name">CLOCK DRIFT</div>
            <div class="value" id="clockDrift">0</div>
        </div>
        <div class="sidebar-metric">
            <div class="name">SEQ EXHAUST</div>
            <div class="value" id="seqExhaust">0</div>
        </div>
    </aside>

    <main class="main">
        <div class="header-bar">
            <h1>Snowflake ID Generator</h1>
            <span class="badge" id="clockStatus">● STABLE</span>
        </div>

        <section class="chart-section">
            <h3>THROUGHPUT HISTORY (last 30 samples)</h3>
            <div class="throughput-chart" id="throughputChart"></div>
        </section>

        <div class="bottom-row">
            <div class="controls-panel">
                <h3>ACTIONS</h3>
                <div class="btn-group">
                    <button class="btn" onclick="generateBatch()">Generate 1K</button>
                    <button class="btn" onclick="stressTest()">Stress 10K</button>
                    <button class="btn" onclick="clearActivity()">Clear Log</button>
                </div>
            </div>
            <div class="activity-panel">
                <h3>ACTIVITY</h3>
                <div id="activityLog"></div>
            </div>
        </div>
    </main>

    <script>
        const throughputHistory = [];
        const maxHistory = 30;

        function log(message, isError = false) {
            const logEl = document.getElementById('activityLog');
            const line = document.createElement('div');
            line.className = 'activity-line';
            const time = new Date().toLocaleTimeString();
            line.innerHTML = `<span class="status-dot${isError ? ' warn' : ''}"></span>[${time}] ${message}`;
            logEl.insertBefore(line, logEl.firstChild);
            while (logEl.children.length > 50) logEl.removeChild(logEl.lastChild);
        }

        function updateChart() {
            const chart = document.getElementById('throughputChart');
            chart.innerHTML = '';
            const max = Math.max(...throughputHistory, 1);
            throughputHistory.forEach(v => {
                const bar = document.createElement('div');
                bar.className = 'chart-bar';
                bar.style.height = (v / max * 100) + '%';
                chart.appendChild(bar);
            });
        }

        async function updateMetrics() {
            try {
                const res = await fetch('/api/metrics');
                const m = await res.json();
                document.getElementById('totalIds').textContent = m.ids_generated.toLocaleString();
                document.getElementById('throughput').innerHTML = Math.floor(m.throughput_per_sec).toLocaleString() + ' <span style="font-size:0.7em">/s</span>';
                document.getElementById('avgLatency').innerHTML = Math.floor(m.avg_latency_ns).toLocaleString() + ' <span style="font-size:0.7em">ns</span>';
                document.getElementById('p99Latency').innerHTML = Math.floor(m.p99_latency_ns).toLocaleString() + ' <span style="font-size:0.7em">ns</span>';
                document.getElementById('clockDrift').textContent = m.clock_drift_events;
                document.getElementById('seqExhaust').textContent = m.sequence_exhaustion_events;
                document.getElementById('clockStatus').textContent = m.clock_drift_events > 0 ? '● DRIFT' : '● STABLE';
                document.getElementById('clockStatus').style.color = m.clock_drift_events > 0 ? '#eab308' : '#22c55e';
                throughputHistory.push(m.throughput_per_sec);
                if (throughputHistory.length > maxHistory) throughputHistory.shift();
                updateChart();
            } catch (e) { log('Metrics error: ' + e.message, true); }
        }

        async function generateBatch() {
            log('Generating 1000 IDs...');
            const t0 = performance.now();
            await Promise.all(Array(1000).fill().map(() => fetch('/api/id')));
            log('Generated 1000 IDs in ' + (performance.now() - t0).toFixed(0) + 'ms');
            updateMetrics();
        }

        async function stressTest() {
            log('Stress test: 10K requests...');
            const t0 = performance.now();
            await Promise.all(Array(10000).fill().map(() => fetch('/api/id')));
            const elapsed = (performance.now() - t0) / 1000;
            log('10K IDs in ' + elapsed.toFixed(2) + 's (' + Math.floor(10000 / elapsed) + '/s)');
            updateMetrics();
        }

        function clearActivity() {
            document.getElementById('activityLog').innerHTML = '';
            log('Log cleared');
        }

        setInterval(updateMetrics, 1000);
        updateMetrics();
        log('Dashboard ready');
    </script>
</body>
</html>
        """;
    }
}
