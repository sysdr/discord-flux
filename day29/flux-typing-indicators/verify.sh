#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔍 Verifying Flux Typing Indicators Demo"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Check if gateway is running
if [ ! -f .gateway.pid ]; then
    echo "❌ Gateway not running. Start with ./start.sh first"
    exit 1
fi

PID=$(cat .gateway.pid)
if ! ps -p $PID > /dev/null 2>&1; then
    echo "❌ Gateway process $PID not found"
    exit 1
fi

echo "✅ Gateway running (PID: $PID)"

# Check dashboard
if curl -s http://localhost:8080 > /dev/null; then
    echo "✅ Dashboard accessible at http://localhost:8080"
else
    echo "❌ Dashboard not responding"
    exit 1
fi

# Check metrics endpoint
METRICS=$(curl -s http://localhost:8080/api/metrics)
if [ -n "$METRICS" ]; then
    echo "✅ Metrics API responding"
    echo "   $METRICS"
else
    echo "❌ Metrics API not responding"
    exit 1
fi

# Check typers endpoint
TYPERS=$(curl -s "http://localhost:8080/api/typers?channel=1001")
if [ -n "$TYPERS" ]; then
    echo "✅ Typers API responding"
    echo "   Active typers: $TYPERS"
else
    echo "❌ Typers API not responding"
    exit 1
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ ALL CHECKS PASSED"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Next steps:"
echo "  1. Open http://localhost:8080 in your browser"
echo "  2. Click 'Simulate 50 Typers' button"
echo "  3. Watch metrics update in real-time"
echo "  4. Run: ./demo.sh 1000 to load test with 1000 typers"
