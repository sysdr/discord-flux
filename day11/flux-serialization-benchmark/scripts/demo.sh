#!/bin/bash

echo "🎬 Running Demo Scenario..."
echo ""
echo "This will:"
echo "  1. Trigger the benchmark"
echo "  2. Wait for completion"
echo "  3. Display performance metrics"
echo ""

# Trigger benchmark
echo "🚀 Triggering benchmark..."
curl -s http://localhost:8080/trigger > /dev/null

# Wait for benchmark to complete (check metrics until operations > 0)
echo "⏳ Waiting for benchmark to complete..."
for i in {1..60}; do
    sleep 2
    ops=$(curl -s http://localhost:8080/metrics | grep -o '"operations":[0-9]*' | head -1 | cut -d: -f2)
    if [ ! -z "$ops" ] && [ "$ops" -gt 0 ]; then
        echo "✅ Benchmark completed!"
        break
    fi
    echo -n "."
done
echo ""

# Display metrics
echo "📊 Current Metrics:"
curl -s http://localhost:8080/metrics | \
    python3 -c "import sys, json; data=json.load(sys.stdin); \
    [print(f\"{e['name']}: {int(e['throughput'])} ops/s, Avg: {e['avgLatency']:.2f}µs, Ops: {e['operations']}\") \
    for e in data['engines']]" 2>/dev/null || \
    curl -s http://localhost:8080/metrics | \
    grep -o '"[^"]*": [^,}]*' | head -20

echo ""
echo "✅ Demo complete! View full metrics at http://localhost:8080"
