package com.flux.gateway.dashboard;

import com.flux.gateway.shard.ShardRegistry;
import com.flux.gateway.shard.ShardSession;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Lightweight HTTP dashboard server using com.sun.net.httpserver.
 *
 * Endpoints:
 *   GET /          — Shard Grid HTML dashboard (single-page)
 *   GET /metrics   — JSON metrics snapshot
 *   GET /shards    — JSON array of all active shard sessions
 */
public final class DashboardServer implements Runnable {

    private final int             port;
    private final ShardRegistry   registry;
    private final MetricsCollector metrics;

    public DashboardServer(int port, ShardRegistry registry, MetricsCollector metrics) {
        this.port     = port;
        this.registry = registry;
        this.metrics  = metrics;
    }

    @Override
    public void run() {
        try {
            var server = HttpServer.create(new InetSocketAddress(port), 32);

            server.createContext("/", exchange -> {
                if (!exchange.getRequestMethod().equals("GET")) { exchange.close(); return; }
                var html = buildDashboardHtml();
                var bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });

            server.createContext("/metrics", exchange -> {
                if (!exchange.getRequestMethod().equals("GET")) { exchange.close(); return; }
                var json  = metrics.toJson();
                var bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });

            server.createContext("/shards", exchange -> {
                if (!exchange.getRequestMethod().equals("GET")) { exchange.close(); return; }
                var json  = buildShardsJson();
                var bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });

            server.createContext("/demo/run", exchange -> {
                if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("POST")) {
                    exchange.close(); return;
                }
                var query = exchange.getRequestURI().getQuery();
                String mode = "normal";
                if (query != null && query.contains("mode=")) {
                    for (var part : query.split("&")) {
                        if (part.startsWith("mode=")) mode = part.substring(5).trim();
                    }
                }
                if (!mode.matches("normal|zombie|conflict")) {
                    sendJson(exchange, 400, "{\"error\":\"Invalid mode. Use normal, zombie, or conflict.\"}");
                    return;
                }
                try {
                    runDemoAsync(mode);
                    sendJson(exchange, 202, "{\"status\":\"started\",\"mode\":\"" + mode + "\",\"message\":\"Demo running. Watch the shard grid.\"}");
                } catch (Exception e) {
                    sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                exchange.close();
            });

            server.start();
            System.out.printf("[Dashboard] Serving at http://localhost:%d%n", port);

        } catch (IOException e) {
            throw new RuntimeException("Dashboard server failed to start", e);
        }
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, int code, String json) throws IOException {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void runDemoAsync(String mode) throws IOException {
        Path userDir = Path.of(System.getProperty("user.dir"));
        Path jarPath = userDir.resolve("target/flux-gateway-shard-1.0.0-SNAPSHOT.jar");
        Path testClasses = userDir.resolve("target/test-classes");
        if (!Files.exists(jarPath) || !Files.isDirectory(testClasses)) {
            throw new IOException("Build required: run mvn package (target/ or target/test-classes missing)");
        }
        String cp = jarPath + java.io.File.pathSeparator + testClasses;
        int hold = "normal".equals(mode) ? 12 : 0;
        var pb = new ProcessBuilder(
            "java", "--enable-preview", "-cp", cp,
            "com.flux.gateway.load.ShardLoadGenerator", "16", mode, String.valueOf(hold)
        );
        pb.directory(userDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        Thread.ofVirtual().name("demo-runner").start(() -> {
            try {
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                p.waitFor();
            } catch (Exception ignored) {}
        });
    }

    private String buildShardsJson() {
        var sessions = registry.allSessions();
        var entries = sessions.stream()
            .map(s -> """
                {"shardId":%d,"numShards":%d,"sessionId":"%s","state":"%s","connectionId":%d}""".formatted(
                    s.identity.shardId(), s.identity.numShards(),
                    s.sessionId, s.state.get(), s.connectionId))
            .collect(Collectors.joining(",\n"));
        return "[" + entries + "]";
    }

    private String buildDashboardHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Flux Gateway — Day 53: Shard Identity Dashboard</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { background: #0d1117; color: #c9d1d9; font-family: 'SF Mono', 'Consolas', monospace;
         font-size: 13px; padding: 20px; }
  h1 { color: #58a6ff; font-size: 18px; margin-bottom: 4px; letter-spacing: 1px; }
  .subtitle { color: #8b949e; font-size: 11px; margin-bottom: 24px; }
  .grid-container { display: flex; gap: 24px; flex-wrap: wrap; }
  .panel { background: #161b22; border: 1px solid #30363d; border-radius: 8px;
           padding: 16px; min-width: 200px; }
  .panel h2 { font-size: 11px; color: #8b949e; text-transform: uppercase;
              letter-spacing: 1px; margin-bottom: 12px; }
  #shard-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
  .shard-cell { background: #21262d; border: 1px solid #30363d; border-radius: 6px;
                padding: 10px 6px; text-align: center; transition: all 0.3s ease;
                cursor: pointer; min-width: 70px; }
  .shard-cell.ready    { background: #0d4429; border-color: #238636; color: #3fb950; }
  .shard-cell.evicted  { background: #3d2b00; border-color: #d29922; color: #d29922;
                         animation: pulse-yellow 0.5s ease; }
  .shard-cell.rejected { background: #3d0014; border-color: #f85149; color: #f85149; }
  .shard-cell.empty    { color: #484f58; }
  @keyframes pulse-yellow { 0%,100% { background:#3d2b00; } 50% { background:#5a3e00; } }
  .shard-id { font-size: 16px; font-weight: bold; }
  .shard-status { font-size: 9px; margin-top: 3px; letter-spacing: 0.5px; }
  .metrics-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
  .metric { background: #21262d; border: 1px solid #30363d; border-radius: 4px; padding: 8px; }
  .metric-label { color: #8b949e; font-size: 9px; text-transform: uppercase; letter-spacing: 0.5px; }
  .metric-value { color: #58a6ff; font-size: 20px; font-weight: bold; margin-top: 2px; }
  .metric-value.green  { color: #3fb950; }
  .metric-value.yellow { color: #d29922; }
  .metric-value.red    { color: #f85149; }
  .controls { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
  button { background: #21262d; border: 1px solid #30363d; color: #c9d1d9; padding: 8px 14px;
           border-radius: 6px; cursor: pointer; font-family: inherit; font-size: 12px;
           transition: all 0.2s; }
  button:hover { background: #30363d; border-color: #58a6ff; color: #58a6ff; }
  button.danger:hover { border-color: #f85149; color: #f85149; }
  .log { background: #010409; border: 1px solid #30363d; border-radius: 6px; padding: 10px;
         height: 160px; overflow-y: auto; font-size: 11px; line-height: 1.6; }
  .log-entry { color: #8b949e; }
  .log-entry.ready    { color: #3fb950; }
  .log-entry.evicted  { color: #d29922; }
  .log-entry.rejected { color: #f85149; }
  .log-entry.info     { color: #58a6ff; }
  .status-bar { margin-top: 16px; color: #8b949e; font-size: 10px; }
</style>
</head>
<body>
<h1>⚡ Flux Gateway — Shard Identity Dashboard</h1>
<p class="subtitle">Day 53 · Phase 4: The Gateway · Real-time shard registration monitor</p>

<div class="grid-container">

  <!-- Shard Grid Panel -->
  <div class="panel" style="flex:1; min-width:340px;">
    <h2>Shard Grid (16 shards)</h2>
    <div id="shard-grid"></div>
  </div>

  <!-- Metrics Panel -->
  <div class="panel" style="min-width:300px;">
    <h2>Live Metrics</h2>
    <div class="metrics-grid">
      <div class="metric">
        <div class="metric-label">Active Shards</div>
        <div class="metric-value green" id="m-active">0</div>
      </div>
      <div class="metric">
        <div class="metric-label">Total Connections</div>
        <div class="metric-value" id="m-total">0</div>
      </div>
      <div class="metric">
        <div class="metric-label">Zombie Evictions</div>
        <div class="metric-value yellow" id="m-evictions">0</div>
      </div>
      <div class="metric">
        <div class="metric-label">Rejected (Conflict)</div>
        <div class="metric-value red" id="m-rejected">0</div>
      </div>
      <div class="metric">
        <div class="metric-label">Identify Latency</div>
        <div class="metric-value" id="m-latency">0ms</div>
      </div>
      <div class="metric">
        <div class="metric-label">JVM Heap Used</div>
        <div class="metric-value" id="m-heap">0MB</div>
      </div>
      <div class="metric">
        <div class="metric-label">Heartbeats</div>
        <div class="metric-value green" id="m-hb">0</div>
      </div>
      <div class="metric">
        <div class="metric-label">Parse Errors</div>
        <div class="metric-value red" id="m-errors">0</div>
      </div>
    </div>

    <h2 style="margin-top:16px;">Controls</h2>
    <div class="controls">
      <button onclick="triggerLoad()">▶ Simulate Bot Fleet (16)</button>
      <button class="danger" onclick="triggerZombie()">☠ Simulate Zombie Shard</button>
      <button onclick="triggerConflict()">⚡ Trigger Shard Conflict</button>
      <button onclick="clearLog()">🗑 Clear Log</button>
    </div>
  </div>

  <!-- Event Log Panel -->
  <div class="panel" style="width:100%;">
    <h2>Event Log</h2>
    <div class="log" id="event-log">
      <div class="log-entry info">[gateway] Dashboard connected. Polling every 500ms.</div>
    </div>
  </div>

</div>

<div class="status-bar">
  Last updated: <span id="last-updated">—</span> &nbsp;|&nbsp;
  Gateway: ws://localhost:8888 &nbsp;|&nbsp;
  Metrics: <a href="/metrics" style="color:#58a6ff;">/metrics</a> &nbsp;|&nbsp;
  Shards: <a href="/shards" style="color:#58a6ff;">/shards</a>
</div>

<script>
const NUM_SHARDS = 16;
let shardState = {};
let prevMetrics = {};

// Initialize shard grid
const grid = document.getElementById('shard-grid');
for (let i = 0; i < NUM_SHARDS; i++) {
  const cell = document.createElement('div');
  cell.className = 'shard-cell empty';
  cell.id = 'shard-' + i;
  cell.innerHTML = '<div class="shard-id">' + i + '</div><div class="shard-status">EMPTY</div>';
  grid.appendChild(cell);
}

function log(msg, cls='') {
  const el = document.getElementById('event-log');
  const entry = document.createElement('div');
  entry.className = 'log-entry ' + cls;
  entry.textContent = '[' + new Date().toISOString().substr(11,12) + '] ' + msg;
  el.appendChild(entry);
  el.scrollTop = el.scrollHeight;
}

function clearLog() {
  document.getElementById('event-log').innerHTML = '';
}

async function poll() {
  try {
    const [metricsRes, shardsRes] = await Promise.all([
      fetch('/metrics'), fetch('/shards')
    ]);
    const metrics = await metricsRes.json();
    const shards  = await shardsRes.json();

    // Update metrics (Active Shards = currently connected)
    const activeShards = Math.max(0, (metrics.shardsConnected || 0) - (metrics.shardsDisconnected || 0));
    document.getElementById('m-active').textContent    = activeShards;
    document.getElementById('m-total').textContent     = metrics.connectionAttempts || 0;
    document.getElementById('m-evictions').textContent = (metrics.zombieEvictions != null ? metrics.zombieEvictions : 0);
    document.getElementById('m-rejected').textContent  = (metrics.identifyRejected != null ? metrics.identifyRejected : 0);
    document.getElementById('m-latency').textContent   = (metrics.identifyLatencyMs != null ? metrics.identifyLatencyMs : 0).toFixed(1) + 'ms';
    document.getElementById('m-heap').textContent      = (metrics.jvmHeapUsedMb != null ? metrics.jvmHeapUsedMb : 0).toFixed(0) + 'MB';
    document.getElementById('m-hb').textContent        = (metrics.heartbeats != null ? metrics.heartbeats : 0);
    document.getElementById('m-errors').textContent    = (metrics.identifyParseErrors != null ? metrics.identifyParseErrors : 0);

    // Diff zombie evictions
    if (prevMetrics.zombieEvictions !== undefined &&
        metrics.zombieEvictions > prevMetrics.zombieEvictions) {
      log('Zombie shard evicted — new session claimed slot', 'evicted');
    }
    if (prevMetrics.identifyRejected !== undefined &&
        metrics.identifyRejected > prevMetrics.identifyRejected) {
      log('Shard conflict detected — duplicate IDENTIFY rejected', 'rejected');
    }
    prevMetrics = metrics;

    // Reset all cells
    for (let i = 0; i < NUM_SHARDS; i++) {
      const cell = document.getElementById('shard-' + i);
      if (cell.className.includes('ready') || cell.className.includes('evicted')) {
        // will be overwritten below
      }
    }

    // Mark empty
    const activeIds = new Set();
    shards.forEach(s => {
      activeIds.add(s.shardId);
      const cell = document.getElementById('shard-' + s.shardId);
      if (cell) {
        const wasEmpty = cell.className.includes('empty');
        cell.className = 'shard-cell ready';
        cell.innerHTML = '<div class="shard-id">' + s.shardId + '</div>' +
                         '<div class="shard-status">' + s.state.replace('READY','●READY') + '</div>';
        if (wasEmpty) log('Shard [' + s.shardId + '/' + s.numShards + '] READY session=' + s.sessionId, 'ready');
      }
    });

    for (let i = 0; i < NUM_SHARDS; i++) {
      if (!activeIds.has(i)) {
        const cell = document.getElementById('shard-' + i);
        if (cell && !cell.className.includes('empty')) {
          cell.className = 'shard-cell empty';
          cell.innerHTML = '<div class="shard-id">' + i + '</div><div class="shard-status">EMPTY</div>';
          log('Shard [' + i + '] disconnected', 'rejected');
        } else if (cell && cell.className.includes('empty')) {
          // already empty, set properly
          cell.className = 'shard-cell empty';
          cell.innerHTML = '<div class="shard-id">' + i + '</div><div class="shard-status">EMPTY</div>';
        }
      }
    }

    document.getElementById('last-updated').textContent = new Date().toLocaleTimeString();
  } catch(e) {
    log('Poll error: ' + e.message, 'rejected');
  }
}

// Flash a shard cell yellow for zombie effect
function flashZombie(shardId) {
  const cell = document.getElementById('shard-' + shardId);
  if (cell) {
    cell.className = 'shard-cell evicted';
    cell.querySelector('.shard-status').textContent = 'ZOMBIE';
    setTimeout(() => poll(), 1200);
  }
}

// Control button handlers — trigger demo via backend
async function triggerLoad() {
  log('Starting bot fleet simulation (16 shards)...', 'info');
  try {
    const r = await fetch('/demo/run?mode=normal', { method: 'POST' });
    const j = await r.json();
    if (r.ok) log('Demo started. Watch the shard grid — connections held 12s.', 'ready');
    else log('Demo error: ' + (j.error || r.status), 'rejected');
  } catch (e) {
    log('Demo failed: ' + e.message, 'rejected');
  }
}
async function triggerZombie() {
  log('Starting zombie shard scenario...', 'info');
  try {
    const r = await fetch('/demo/run?mode=zombie', { method: 'POST' });
    const j = await r.json();
    if (r.ok) log('Zombie demo started.', 'ready');
    else log('Demo error: ' + (j.error || r.status), 'rejected');
  } catch (e) {
    log('Demo failed: ' + e.message, 'rejected');
  }
}
async function triggerConflict() {
  log('Starting shard conflict scenario...', 'info');
  try {
    const r = await fetch('/demo/run?mode=conflict', { method: 'POST' });
    const j = await r.json();
    if (r.ok) log('Conflict demo started. One READY, one REJECTED.', 'ready');
    else log('Demo error: ' + (j.error || r.status), 'rejected');
  } catch (e) {
    log('Demo failed: ' + e.message, 'rejected');
  }
}

setInterval(poll, 500);
poll();
</script>
</body>
</html>
""";
    }
}
