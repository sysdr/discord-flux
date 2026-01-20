#!/bin/bash

echo "🔍 Verifying Serialization Benchmark..."
echo ""

# Find Java process
PID=$(jps | grep FluxSerializationApp | cut -d' ' -f1)

if [ -z "$PID" ]; then
    echo "❌ Error: FluxSerializationApp not running"
    echo "   Start it with: bash scripts/start.sh"
    exit 1
fi

echo "✅ Found running process: PID $PID"
echo ""

echo "📊 Memory Statistics:"
jcmd $PID GC.heap_info | grep -A 5 "Heap"

echo ""
echo "🧵 Thread Statistics:"
jcmd $PID Thread.print | grep "java.lang.VirtualThread" | wc -l | \
    xargs -I {} echo "   Virtual Threads: {}"

echo ""
echo "📈 Metrics Snapshot:"
curl -s http://localhost:8080/metrics | jq -r '
.engines[] | 
"  \(.name):
    Throughput: \(.throughput | floor) ops/s
    Avg Latency: \(.avgLatency)µs  
    P99 Latency: \(.p99Latency)µs
    Operations: \(.operations)
"'

echo ""
echo "✅ Verification complete!"
echo "   View live dashboard: http://localhost:8080"
