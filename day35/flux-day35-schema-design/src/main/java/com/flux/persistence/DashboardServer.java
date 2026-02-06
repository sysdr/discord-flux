package com.flux.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class DashboardServer {
    private final CqlSession session;
    private final HttpServer server;
    private final PartitionAnalyzer analyzer;

    public DashboardServer(CqlSession session, int port) throws IOException {
        this.session = session;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.analyzer = new PartitionAnalyzer(session);
        setupRoutes();
    }

    private void setupRoutes() {
        server.createContext("/", exchange -> {
            var response = getDashboardHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) { os.write(response.getBytes(StandardCharsets.UTF_8)); }
        });
        server.createContext("/api/stats", exchange -> {
            var stats = analyzer.analyzePartitions(12345L);
            var json = new StringBuilder("[");
            for (int i = 0; i < stats.size(); i++) {
                var s = stats.get(i);
                json.append(String.format("{\"bucket\":%d,\"count\":%d}", s.bucket(), s.messageCount()));
                if (i < stats.size() - 1) json.append(",");
            }
            json.append("]");
            var response = json.toString();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) { os.write(response.getBytes(StandardCharsets.UTF_8)); }
        });
    }

    public void start() {
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("🌐 Dashboard running at http://localhost:" + server.getAddress().getPort());
    }

    private String getDashboardHtml() {
        return """
<!DOCTYPE html>
<html>
<head><title>Flux Day 35: Partition Dashboard</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Courier New',monospace;background:#0a0e27;color:#00ff88;padding:20px}
.header{text-align:center;border-bottom:2px solid #00ff88;padding-bottom:20px;margin-bottom:30px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:15px;margin:30px 0}
.bucket{background:#1a1f3a;border:2px solid #00ff88;padding:20px;text-align:center;border-radius:8px;transition:all .3s}
.bucket:hover{transform:scale(1.05);box-shadow:0 0 20px #00ff88}
.bucket-hot{background:#ff3366;border-color:#ff3366;animation:pulse 2s infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.7}}
.metrics{display:flex;justify-content:space-around;margin:30px 0}
.metric{text-align:center;padding:20px;background:#1a1f3a;border-radius:8px;flex:1;margin:0 10px}
.metric-value{font-size:2em;font-weight:bold;color:#00ff88}
button{background:#00ff88;color:#0a0e27;border:none;padding:15px 30px;font-size:1em;font-weight:bold;cursor:pointer;border-radius:5px;margin:10px}
button:hover{background:#00cc66}
.no-data{text-align:center;padding:40px;opacity:.8}
</style>
</head>
<body>
<div class="header"><h1>FLUX DAY 35: PARTITION ANALYZER</h1><p>Real-time Cassandra Partition Distribution</p></div>
<div class="metrics" id="metrics">
<div class="metric"><div class="metric-value" id="totalPartitions">0</div><div>Total Partitions</div></div>
<div class="metric"><div class="metric-value" id="totalMessages">0</div><div>Total Messages</div></div>
<div class="metric"><div class="metric-value" id="hotPartitions">0</div><div>Hot Partitions</div></div>
</div>
<div style="text-align:center">
<button onclick="loadStats()">Refresh Stats</button>
<button onclick="runDemo()">Run Demo (24h Write Storm)</button>
</div>
<div class="grid" id="partitionGrid"></div>
<div class="no-data" id="noData" style="display:none">No data yet. Run ./demo.sh to populate metrics.</div>
<script>
async function loadStats(){
const r=await fetch('/api/stats');const stats=await r.json();
document.getElementById('partitionGrid').innerHTML='';document.getElementById('noData').style.display='none';
let totalMessages=0,hotCount=0;
const avgCount=stats.length>0?stats.reduce((s,x)=>s+x.count,0)/stats.length:0;
stats.forEach(stat=>{totalMessages+=stat.count;if(avgCount>0&&stat.count>avgCount*5)hotCount++;
const d=document.createElement('div');d.className=stat.count>avgCount*5&&avgCount>0?'bucket bucket-hot':'bucket';
d.innerHTML='<div style="font-size:.8em;opacity:.7">Bucket '+stat.bucket+'</div><div style="font-size:1.5em;margin:10px 0">'+stat.count+'</div><div style="font-size:.7em">messages</div>';
document.getElementById('partitionGrid').appendChild(d);});
if(stats.length===0)document.getElementById('noData').style.display='block';
document.getElementById('totalPartitions').textContent=stats.length;
document.getElementById('totalMessages').textContent=totalMessages.toLocaleString();
document.getElementById('hotPartitions').textContent=hotCount;
}
function runDemo(){alert('Run ./demo.sh in terminal to populate data, then click Refresh.');}
setInterval(loadStats,5000);loadStats();
</script>
</body>
</html>
""";
    }
}
