#!/bin/bash

echo "🔥 Running load test to populate dashboard metrics..."
echo "   This will generate activity so the dashboard shows non-zero values."
echo ""

cd "$(dirname "$0")"

# Check if server is running
if ! pgrep -f "com.flux.gateway.GatewayServer" > /dev/null; then
    echo "❌ Gateway Server is not running!"
    echo "   Start it first with: ./start.sh"
    exit 1
fi

# Run load test
echo "📊 Generating metrics (this may take 30-60 seconds)..."
timeout 60 java -cp target/classes:$(mvn dependency:build-classpath -q -DincludeScope=compile -Dmdep.outputFile=/dev/stdout) com.flux.gateway.LoadTest 2>&1 | grep -E "Starting|completed|error" | tail -5

echo ""
echo "✅ Load test completed!"
echo "📊 Check dashboard at http://localhost:9090"
echo ""
echo "Current metrics:"
curl -s http://localhost:9090/metrics | python3 -c "import sys, json; d=json.load(sys.stdin); print(f'  Processed: {d[\"processed\"]} tasks'); print(f'  p50 Latency: {d[\"p50Latency\"]/1000:.2f}ms'); print(f'  p99 Latency: {d[\"p99Latency\"]/1000:.2f}ms')" 2>/dev/null || curl -s http://localhost:9090/metrics
