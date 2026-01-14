#!/bin/bash

cd "$(dirname "$0")"

echo "🔍 Flux Gateway Verification"
echo "============================="
echo ""

# Check if gateway is running
if [ ! -f gateway.pid ]; then
    echo "❌ Gateway not started. Run ./start.sh first"
    exit 1
fi

PID=$(cat gateway.pid)

if ! ps -p $PID > /dev/null; then
    echo "❌ Gateway process not found (PID: $PID)"
    exit 1
fi

echo "✅ Gateway running (PID: $PID)"

# Check ports
echo ""
echo "Checking ports..."

if lsof -i :9000 -sTCP:LISTEN > /dev/null 2>&1 || netstat -an | grep -q "9000.*LISTEN" 2>/dev/null; then
    echo "✅ Gateway listening on port 9000"
else
    echo "❌ Gateway port 9000 not listening"
fi

if lsof -i :8080 -sTCP:LISTEN > /dev/null 2>&1 || netstat -an | grep -q "8080.*LISTEN" 2>/dev/null; then
    echo "✅ Dashboard listening on port 8080"
else
    echo "❌ Dashboard port 8080 not listening"
fi

# Test connection
echo ""
echo "Testing connection..."

response=$(curl -s -w "%{http_code}" -o /dev/null http://localhost:8080)

if [ "$response" -eq 200 ]; then
    echo "✅ Dashboard responding (HTTP 200)"
else
    echo "❌ Dashboard not responding"
fi

# Check metrics
echo ""
echo "Fetching metrics..."
curl -s http://localhost:8080/api/metrics | python3 -m json.tool 2>/dev/null || echo "Raw metrics available"

echo ""
echo "✅ Verification complete!"
echo ""
echo "Next steps:"
echo "  1. Open http://localhost:8080 in browser"
echo "  2. Run './demo.sh' to simulate 100 clients"
echo "  3. Monitor JVM with: jconsole $PID"
