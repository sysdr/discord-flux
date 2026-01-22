#!/bin/bash
# Test script to verify dashboard updates in real-time

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧪 Testing Dashboard Real-time Updates"
echo ""

# Check if dashboard is running
if ! /usr/bin/nc -z localhost 9090 2>/dev/null; then
    echo "❌ Dashboard not running on port 9090"
    echo "Starting dashboard..."
    /usr/bin/bash start-dashboard.sh > /tmp/dashboard-test.log 2>&1 &
    sleep 3
fi

echo "📊 Fetching metrics 5 times with 2 second intervals..."
echo ""

for i in {1..5}; do
    echo "--- Reading $i ---"
    /usr/bin/curl -s http://localhost:9090/metrics 2>/dev/null | /usr/bin/python3 -c "
import sys, json, time
d = json.load(sys.stdin)
print(f'  Total Attempts: {d.get(\"totalAttempts\", 0)}')
print(f'  Active Connections: {d.get(\"activeConnections\", 0)}')
print(f'  Messages Sent: {d.get(\"messagesSent\", 0)}')
print(f'  Success Rate: {d.get(\"successRate\", 0):.2f}%')
print(f'  Timestamp: {time.strftime(\"%H:%M:%S\")}')
" 2>/dev/null || echo "  Error fetching metrics"
    echo ""
    if [ $i -lt 5 ]; then
        sleep 2
    fi
done

echo "✅ Test complete!"
echo ""
echo "If you see the metrics updating, the dashboard is working correctly."
echo "Open http://localhost:9090 in your browser to see the real-time dashboard."
