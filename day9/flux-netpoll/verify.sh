#!/bin/bash

# Change to script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔍 Verifying Reactor Performance..."

# Check if reactor is running
if ! pgrep -f "ReactorMain" > /dev/null; then
    echo "❌ Reactor is not running"
    exit 1
fi

REACTOR_PID=$(pgrep -f "ReactorMain")

# Check thread count (should be low due to Virtual Threads)
THREAD_COUNT=$(ps -o nlwp -p $REACTOR_PID | tail -1 | tr -d ' ')
echo "✓ JVM Thread count: $THREAD_COUNT"

if [ $THREAD_COUNT -lt 100 ]; then
    echo "✓ Thread count is healthy (Virtual Threads working)"
else
    echo "⚠ High thread count detected (expected <100)"
fi

# Check CPU usage
CPU_USAGE=$(ps -p $REACTOR_PID -o %cpu | tail -1 | tr -d ' ' | cut -d. -f1)
echo "✓ CPU Usage: ${CPU_USAGE}%"

# Query stats endpoint
STATS=$(curl -s http://localhost:8080/api/stats)
ACTIVE_CONNS=$(echo $STATS | grep -o '"activeConnections":[0-9]*' | grep -o '[0-9]*')

echo "✓ Active connections: $ACTIVE_CONNS"

if [ $ACTIVE_CONNS -gt 0 ]; then
    echo "✓ Reactor is accepting connections"
else
    echo "⚠ No active connections detected"
fi

echo ""
echo "=== Verification Complete ==="
