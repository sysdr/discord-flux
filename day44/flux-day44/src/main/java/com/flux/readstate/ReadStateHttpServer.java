package com.flux.readstate;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Lightweight HTTP server using com.sun.net.httpserver.
 *
 * Endpoints:
 *   GET  /dashboard       → Serve HTML dashboard
 *   GET  /api/metrics     → JSON metrics snapshot
 *   GET  /api/states      → JSON array of entry snapshots for heatmap
 *   POST /api/ack         → Submit a manual ack (body: JSON)
 *   POST /api/simulate    → Trigger simulation scenarios
 */
public final class ReadStateHttpServer {

    private final HttpServer        httpServer;
    private final AckTracker        ackTracker;
    private final CassandraSink     cassandraSink;
    private final ChannelSimulator  channelSim;
    private final SnowflakeIdGenerator snowflake;

    private static final String DASHBOARD_HTML = buildDashboardHtml();

    public ReadStateHttpServer(
        int port,
        AckTracker ackTracker,
        CassandraSink cassandraSink,
        ChannelSimulator channelSim,
        SnowflakeIdGenerator snowflake
    ) throws IOException {
        this.ackTracker   = ackTracker;
        this.cassandraSink = cassandraSink;
        this.channelSim   = channelSim;
        this.snowflake    = snowflake;

        httpServer = HttpServer.create(new InetSocketAddress(port), 128);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        httpServer.createContext("/", exchange -> {
            var path   = exchange.getRequestURI().getPath();
            var method = exchange.getRequestMethod();
            try {
                switch (path) {
                    case "/", "/dashboard" -> serveDashboard(exchange);
                    case "/api/metrics"    -> serveMetrics(exchange);
                    case "/api/states"     -> serveStates(exchange);
                    case "/api/ack" -> {
                        if ("POST".equals(method)) handleAck(exchange);
                        else sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    }
                    case "/api/simulate" -> {
                        if ("POST".equals(method)) handleSimulate(exchange);
                        else sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    }
                    default -> sendJson(exchange, 404, "{\"error\":\"Not Found\"}");
                }
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }

    public void start() {
        httpServer.start();
    }

    public void stop() {
        httpServer.stop(2);
    }

    // ── Handlers ─────────────────────────────────────────────────────

    private void serveDashboard(HttpExchange ex) throws IOException {
        byte[] body = DASHBOARD_HTML.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private void serveMetrics(HttpExchange ex) throws IOException {
        var m  = ackTracker.getMetrics();
        var sb = new StringBuilder("{");
        sb.append("\"totalAcks\":").append(m.totalAcks()).append(",");
        sb.append("\"staleAcks\":").append(m.staleAcks()).append(",");
        sb.append("\"newEntries\":").append(m.newEntries()).append(",");
        sb.append("\"dirtyQueueDepth\":").append(m.dirtyQueueDepth()).append(",");
        sb.append("\"totalEntries\":").append(m.totalEntries()).append(",");
        sb.append("\"cassandraWrites\":").append(m.cassandraWrites()).append(",");
        sb.append("\"cassandraBatches\":").append(m.cassandraBatchCount()).append(",");
        sb.append("\"coalescingRatio\":").append(String.format("%.2f", m.coalescingRatio())).append(",");
        sb.append("\"ackRatePerSec\":").append(String.format("%.1f", m.ackRatePerSec())).append(",");
        sb.append("\"cassandraWriteRatePerSec\":").append(String.format("%.1f", m.cassandraWriteRatePerSec())).append(",");
        sb.append("\"totalChannelMessages\":").append(channelSim.getTotalMessages()).append(",");
        sb.append("\"cassandraRowsWritten\":").append(cassandraSink.getTotalRowsWritten()).append(",");
        sb.append("\"injectingTimeout\":").append(cassandraSink.isInjectingTimeout());
        sb.append("}");
        sendJson(ex, 200, sb.toString());
    }

    private void serveStates(HttpExchange ex) throws IOException {
        var snapshots = ackTracker.getEntrySnapshots(400);
        var sb = new StringBuilder("[");
        for (int i = 0; i < snapshots.size(); i++) {
            var s = snapshots.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"userId\":").append(s.userId())
              .append(",\"channelId\":").append(s.channelId())
              .append(",\"state\":").append(s.state())
              .append(",\"mentionCount\":").append(s.mentionCount())
              .append(",\"unreadCount\":").append(s.unreadCount())
              .append("}");
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    private void handleAck(HttpExchange ex) throws IOException {
        var body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        // Simple JSON parse for {"userId":X,"channelId":Y,"messageId":Z,"mentionDelta":N}
        try {
            long userId      = extractLong(body, "userId");
            long channelId   = extractLong(body, "channelId");
            long messageId   = extractLong(body, "messageId");
            int mentionDelta = (int) extractLong(body, "mentionDelta");
            var result       = ackTracker.ack(new AckCommand(userId, channelId, messageId, mentionDelta));
            sendJson(ex, 200, "{\"result\":\"" + result + "\"}");
        } catch (Exception e) {
            sendJson(ex, 400, "{\"error\":\"Invalid JSON: " + e.getMessage() + "\"}");
        }
    }

    private void handleSimulate(HttpExchange ex) throws IOException {
        var query    = ex.getRequestURI().getQuery();
        var scenario = query != null && query.startsWith("scenario=")
            ? query.substring("scenario=".length()) : "unknown";

        switch (scenario) {
            case "message_burst" -> {
                channelSim.triggerBurst();
                sendJson(ex, 200, "{\"result\":\"Message burst triggered\"}");
            }
            case "batch_ack" -> {
                Thread.ofVirtual().start(() -> {
                    var rng = java.util.concurrent.ThreadLocalRandom.current();
                    for (int i = 0; i < 1000; i++) {
                        long uid  = rng.nextLong(1, ChannelSimulator.USER_COUNT + 1);
                        long cid  = rng.nextLong(1, ChannelSimulator.CHANNEL_COUNT + 1);
                        long mid  = snowflake.nextId();
                        ackTracker.onNewMessage(cid, mid);
                        ackTracker.ack(new AckCommand(uid, cid, mid, 0));
                    }
                });
                sendJson(ex, 200, "{\"result\":\"1000 acks dispatched\"}");
            }
            case "reconnect_storm" -> {
                Thread.ofVirtual().start(() -> {
                    for (int uid = 1; uid <= ChannelSimulator.USER_COUNT; uid++) {
                        final int userId = uid;
                        Thread.ofVirtual().start(() -> {
                            for (int cid = 1; cid <= ChannelSimulator.CHANNEL_COUNT; cid++) {
                                ackTracker.getSnapshot(userId, cid);
                            }
                        });
                    }
                });
                sendJson(ex, 200, "{\"result\":\"Reconnect storm for 20 users triggered\"}");
            }
            case "inject_timeout" -> {
                cassandraSink.setInjectTimeout(!cassandraSink.isInjectingTimeout());
                sendJson(ex, 200, "{\"result\":\"Timeout injection: " + cassandraSink.isInjectingTimeout() + "\"}");
            }
            case "force_flush" -> {
                Thread.ofVirtual().start(() -> {
                    var batch = ackTracker.drainDirtyBatch();
                    while (!batch.isEmpty()) {
                        cassandraSink.batchWrite(batch, ackTracker);
                        batch = ackTracker.drainDirtyBatch();
                    }
                });
                sendJson(ex, 200, "{\"result\":\"Force flush dispatched\"}");
            }
            default -> sendJson(ex, 400, "{\"error\":\"Unknown scenario: " + scenario + "\"}");
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private static long extractLong(String json, String key) {
        int idx = json.indexOf("\"" + key + "\":");
        if (idx == -1) throw new IllegalArgumentException("Missing key: " + key);
        int start = idx + key.length() + 3;
        int end   = start;
        if (json.charAt(end) == '-') end++;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return Long.parseLong(json.substring(start, end));
    }

    // ── Dashboard HTML (inline text block) ──────────────────────────
    private static String buildDashboardHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Flux Day 44 — Read State / Ack Tracker Dashboard</title>
  <style>
    :root {
      --bg: #0d1117; --surface: #161b22; --border: #30363d;
      --text: #e6edf3; --muted: #8b949e;
      --green: #2ea043; --yellow: #e3b341; --orange: #f0883e;
      --red: #f85149; --blue: #58a6ff; --purple: #bc8cff;
    }
    * { margin:0; padding:0; box-sizing:border-box; }
    body { background:var(--bg); color:var(--text); font-family:'Courier New',monospace; font-size:13px; }
    header { background:var(--surface); border-bottom:1px solid var(--border); padding:12px 20px;
             display:flex; align-items:center; gap:16px; }
    header h1 { font-size:15px; font-weight:700; color:var(--blue); }
    header span { color:var(--muted); font-size:11px; }
    .status-dot { width:8px;height:8px;border-radius:50%;background:var(--green);animation:pulse 2s infinite; }
    @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.4} }
    .layout { display:grid; grid-template-columns:1fr 340px; gap:16px; padding:16px; }
    .card { background:var(--surface); border:1px solid var(--border); border-radius:6px; padding:16px; }
    .card h2 { font-size:11px; text-transform:uppercase; letter-spacing:1px; color:var(--muted);
               margin-bottom:12px; border-bottom:1px solid var(--border); padding-bottom:8px; }
    /* Metrics grid */
    .metrics { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; margin-bottom:16px; }
    .metric-card { background:var(--surface); border:1px solid var(--border); border-radius:6px;
                   padding:12px; text-align:center; }
    .metric-card .val { font-size:22px; font-weight:700; color:var(--blue); }
    .metric-card .lbl { font-size:10px; color:var(--muted); margin-top:4px; text-transform:uppercase; }
    .metric-card .sub { font-size:10px; color:var(--green); margin-top:2px; }
    /* Heatmap */
    #heatmap { display:grid; grid-template-columns:repeat(20,1fr); gap:2px; }
    .cell { width:100%; aspect-ratio:1; border-radius:2px; transition:all .3s;
            cursor:pointer; border:1px solid transparent; }
    .cell.cold     { background:#1e2530; }
    .cell.clean    { background:#2ea04366; border-color:#2ea043; }
    .cell.dirty    { background:#e3b34166; border-color:#e3b341; }
    .cell.flushing { background:#58a6ff66; border-color:#58a6ff; }
    .cell.unread   { background:#f8514966; border-color:#f85149; }
    .cell:hover    { transform:scale(1.2); z-index:10; }
    /* Tooltip */
    #tooltip { position:fixed; background:#21262d; border:1px solid var(--border);
               border-radius:4px; padding:8px; font-size:11px; pointer-events:none;
               display:none; z-index:100; }
    /* Legend */
    .legend { display:flex; gap:16px; margin-top:12px; flex-wrap:wrap; }
    .legend-item { display:flex; align-items:center; gap:6px; font-size:11px; color:var(--muted); }
    .legend-dot { width:10px;height:10px;border-radius:2px; }
    /* Controls */
    .btn-grid { display:grid; grid-template-columns:1fr 1fr; gap:8px; margin-bottom:12px; }
    button { background:#21262d; border:1px solid var(--border); color:var(--text);
             padding:8px 12px; border-radius:4px; cursor:pointer; font-size:11px;
             font-family:inherit; transition:all .2s; }
    button:hover { background:#30363d; border-color:var(--blue); color:var(--blue); }
    button.danger:hover { border-color:var(--red); color:var(--red); }
    button.active { background:#1f3a1f; border-color:var(--green); color:var(--green); }
    /* Log */
    #log { height:160px; overflow-y:auto; background:#0d1117; border:1px solid var(--border);
           border-radius:4px; padding:8px; font-size:10px; color:var(--muted); }
    #log .entry { margin-bottom:3px; }
    #log .entry span.ts { color:#444; }
    #log .entry span.ok { color:var(--green); }
    #log .entry span.warn { color:var(--yellow); }
    #log .entry span.err { color:var(--red); }
    /* Coalescing bar */
    .ratio-bar-wrap { background:#0d1117; border-radius:4px; height:20px; margin-top:8px;
                      border:1px solid var(--border); overflow:hidden; }
    .ratio-bar { height:100%; background:linear-gradient(90deg,var(--green),var(--blue));
                 transition:width .5s; display:flex; align-items:center; justify-content:flex-end;
                 padding-right:6px; font-size:10px; min-width:30px; }
    .axis-labels { display:flex; justify-content:space-between; font-size:9px; color:var(--muted); margin-top:2px; }
  </style>
</head>
<body>
<header>
  <div class="status-dot"></div>
  <h1>Flux Day 44 — Read State / Ack Tracker</h1>
  <span>Write Coalescing Engine · Dirty Queue Depth · VarHandle CAS</span>
</header>

<div id="tooltip"></div>

<div style="padding:16px 16px 0">
  <div class="metrics" id="metrics-grid">
    <div class="metric-card"><div class="val" id="m-totalAcks">0</div><div class="lbl">Total Acks</div><div class="sub" id="m-ackRate">0/s</div></div>
    <div class="metric-card"><div class="val" id="m-dirtyDepth">0</div><div class="lbl">Dirty Queue</div><div class="sub">pending flush</div></div>
    <div class="metric-card"><div class="val" id="m-ratio">—</div><div class="lbl">Coalescing Ratio</div><div class="sub">acks per DB write</div></div>
    <div class="metric-card"><div class="val" id="m-cassWrites">0</div><div class="lbl">Cassandra Writes</div><div class="sub" id="m-cassRate">0 rows/s</div></div>
  </div>
</div>

<div class="layout">
  <!-- Left: Heatmap -->
  <div class="card">
    <h2>Read State Heatmap — Users (rows) × Channels (cols)</h2>
    <div id="heatmap"></div>
    <div class="legend">
      <div class="legend-item"><div class="legend-dot" style="background:#1e2530;border:1px solid #444"></div> COLD</div>
      <div class="legend-item"><div class="legend-dot" style="background:#2ea04366;border:1px solid #2ea043"></div> CLEAN</div>
      <div class="legend-item"><div class="legend-dot" style="background:#e3b34166;border:1px solid #e3b341"></div> DIRTY</div>
      <div class="legend-item"><div class="legend-dot" style="background:#58a6ff66;border:1px solid #58a6ff"></div> FLUSHING</div>
      <div class="legend-item"><div class="legend-dot" style="background:#f8514966;border:1px solid #f85149"></div> UNREAD</div>
    </div>
    <div style="margin-top:12px">
      <div style="font-size:10px;color:var(--muted);margin-bottom:4px;">WRITE COALESCING RATIO (x:1)</div>
      <div class="ratio-bar-wrap">
        <div class="ratio-bar" id="ratio-bar" style="width:0%">—</div>
      </div>
      <div class="axis-labels"><span>1:1 (no coalescing)</span><span>100:1 (max efficiency)</span></div>
    </div>
  </div>

  <!-- Right: Controls + Log -->
  <div style="display:flex;flex-direction:column;gap:16px">
    <div class="card">
      <h2>Simulation Controls</h2>
      <div class="btn-grid">
        <button onclick="simulate('message_burst')">📨 Message Burst</button>
        <button onclick="simulate('batch_ack')">✅ Batch Ack (1K)</button>
        <button onclick="simulate('reconnect_storm')">⚡ Reconnect Storm</button>
        <button onclick="simulate('force_flush')">💾 Force Flush</button>
        <button id="btn-timeout" class="danger" onclick="toggleTimeout()">🔥 Inject Timeout</button>
        <button onclick="resetLog()">🗑 Clear Log</button>
      </div>
    </div>

    <div class="card" style="flex:1">
      <h2>Event Log</h2>
      <div id="log"></div>
    </div>

    <div class="card">
      <h2>Write Coalescing Explained</h2>
      <p style="color:var(--muted);font-size:11px;line-height:1.6">
        Each cell in the heatmap = one (user, channel) pair.<br><br>
        <span style="color:var(--yellow)">■ DIRTY</span> = ack received, not yet written to Cassandra.<br>
        <span style="color:var(--blue)">■ FLUSHING</span> = batch write in flight.<br>
        <span style="color:var(--green)">■ CLEAN</span> = matches Cassandra.<br><br>
        The <strong>Coalescing Ratio</strong> shows how many acks were absorbed per actual DB write.
        A ratio of 50:1 means 98% write reduction.
      </p>
    </div>
  </div>
</div>

<script>
  const USER_COUNT    = 20;
  const CHANNEL_COUNT = 20;
  const heatmap       = document.getElementById('heatmap');
  let   stateMap      = {};
  let   timeoutActive = false;

  // Build empty grid
  for (let u = 1; u <= USER_COUNT; u++) {
    for (let c = 1; c <= CHANNEL_COUNT; c++) {
      const cell = document.createElement('div');
      cell.className = 'cell cold';
      cell.id        = `cell-${u}-${c}`;
      cell.title     = `U${u} C${c}`;
      cell.addEventListener('mousemove', e => showTooltip(e, u, c));
      cell.addEventListener('mouseleave', () => hideTooltip());
      heatmap.appendChild(cell);
    }
  }

  function showTooltip(e, u, c) {
    const key  = `${u}-${c}`;
    const data = stateMap[key];
    const tt   = document.getElementById('tooltip');
    if (!data) { tt.style.display='none'; return; }
    const stateNames = ['CLEAN','DIRTY','FLUSHING','COLD'];
    const stateLabel = data.state >= 0 ? stateNames[data.state] : 'COLD';
    tt.innerHTML = `<b>U${u} × C${c}</b><br>
      State: <b>${stateLabel}</b><br>
      Unread: <b>${data.unreadCount}</b><br>
      Mentions: <b>${data.mentionCount}</b>`;
    tt.style.display = 'block';
    tt.style.left    = (e.clientX + 12) + 'px';
    tt.style.top     = (e.clientY - 10) + 'px';
  }
  function hideTooltip() { document.getElementById('tooltip').style.display='none'; }

  async function pollMetrics() {
    try {
      const r = await fetch('/api/metrics');
      const m = await r.json();
      document.getElementById('m-totalAcks').textContent  = fmt(m.totalAcks);
      document.getElementById('m-ackRate').textContent    = m.ackRatePerSec.toFixed(0) + '/s';
      document.getElementById('m-dirtyDepth').textContent = m.dirtyQueueDepth;
      document.getElementById('m-cassWrites').textContent = fmt(m.cassandraWrites);
      document.getElementById('m-cassRate').textContent   = m.cassandraWriteRatePerSec.toFixed(0) + ' rows/s';

      const ratio = m.coalescingRatio;
      document.getElementById('m-ratio').textContent = ratio > 0 ? ratio.toFixed(1) + ':1' : '—';
      const pct = Math.min(100, ratio);
      const bar = document.getElementById('ratio-bar');
      bar.style.width   = pct + '%';
      bar.textContent   = ratio > 2 ? ratio.toFixed(1) + ':1' : '';

      document.getElementById('btn-timeout').className =
        m.injectingTimeout ? 'danger active' : 'danger';

      if (m.dirtyQueueDepth > 50) logWarn(`Dirty queue depth: ${m.dirtyQueueDepth}`);
    } catch(e) {}
  }

  async function pollStates() {
    try {
      const r  = await fetch('/api/states');
      const ss = await r.json();
      // Reset all cells to cold
      document.querySelectorAll('.cell').forEach(c => c.className = 'cell cold');
      stateMap = {};
      ss.forEach(s => {
        const cell = document.getElementById(`cell-${s.userId}-${s.channelId}`);
        if (!cell) return;
        stateMap[`${s.userId}-${s.channelId}`] = s;
        let cls = 'cell ';
        if (s.unreadCount > 0 && s.state === 0) cls += 'unread';
        else if (s.state === 0) cls += 'clean';
        else if (s.state === 1) cls += 'dirty';
        else if (s.state === 2) cls += 'flushing';
        else cls += 'cold';
        cell.className = cls;
      });
    } catch(e) {}
  }

  async function simulate(scenario) {
    const r = await fetch(`/api/simulate?scenario=${scenario}`, {method:'POST'});
    const d = await r.json();
    logOk(d.result || JSON.stringify(d));
  }

  async function toggleTimeout() {
    await simulate('inject_timeout');
    timeoutActive = !timeoutActive;
    if (timeoutActive) logWarn('Cassandra timeout injection ENABLED');
    else logOk('Cassandra timeout injection disabled');
  }

  function logOk(msg)   { addLog(msg, 'ok');   }
  function logWarn(msg) { addLog(msg, 'warn'); }
  function logErr(msg)  { addLog(msg, 'err');  }
  function addLog(msg, type) {
    const log   = document.getElementById('log');
    const entry = document.createElement('div');
    entry.className = 'entry';
    const ts    = new Date().toTimeString().substring(0,8);
    entry.innerHTML = `<span class="ts">[${ts}]</span> <span class="${type}">${msg}</span>`;
    log.insertBefore(entry, log.firstChild);
    if (log.children.length > 50) log.removeChild(log.lastChild);
  }
  function resetLog() { document.getElementById('log').innerHTML = ''; }

  function fmt(n) {
    if (n >= 1_000_000) return (n/1_000_000).toFixed(1) + 'M';
    if (n >= 1_000)     return (n/1_000).toFixed(1) + 'K';
    return String(n);
  }

  setInterval(pollMetrics, 1000);
  setInterval(pollStates,  1500);
  pollMetrics();
  pollStates();
  logOk('Dashboard connected. Server on port 8085.');
</script>
</body>
</html>
""";
    }
}
