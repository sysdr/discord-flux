#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔍 Verifying Flux Service Discovery..."
echo ""

# Check Redis
if ! command -v redis-cli &> /dev/null; then
    echo "❌ redis-cli not found"
    exit 1
fi

if ! redis-cli ping > /dev/null 2>&1; then
    echo "❌ Redis is not running"
    exit 1
fi

echo "✅ Redis is running"

# Check node count
NODE_COUNT=$(redis-cli KEYS "gateway:nodes:*" | wc -l)
echo "✅ Registered nodes: $NODE_COUNT"

# Check dashboard
if curl -s http://localhost:8080 > /dev/null 2>&1; then
    echo "✅ Dashboard is accessible"
else
    echo "❌ Dashboard is not accessible"
    exit 1
fi

# Fetch metrics
METRICS=$(curl -s http://localhost:8080/api/metrics)
REGISTRATIONS=$(echo $METRICS | grep -o '"registrations":[0-9]*' | cut -d: -f2)
HEARTBEAT_SUCCESS=$(echo $METRICS | grep -o '"heartbeatSuccesses":[0-9]*' | cut -d: -f2)
HEARTBEAT_FAILURES=$(echo $METRICS | grep -o '"heartbeatFailures":[0-9]*' | cut -d: -f2)

echo "✅ Total registrations: $REGISTRATIONS"
echo "✅ Heartbeat successes: $HEARTBEAT_SUCCESS"
echo "✅ Heartbeat failures: $HEARTBEAT_FAILURES"

# Calculate success rate
if [ "$HEARTBEAT_SUCCESS" -gt 0 ]; then
    TOTAL_HEARTBEATS=$((HEARTBEAT_SUCCESS + HEARTBEAT_FAILURES))
    SUCCESS_RATE=$((HEARTBEAT_SUCCESS * 100 / TOTAL_HEARTBEATS))
    echo "✅ Heartbeat success rate: ${SUCCESS_RATE}%"
    
    if [ "$SUCCESS_RATE" -lt 95 ]; then
        echo "⚠️  Warning: Success rate below 95%"
    fi
fi

# Check Virtual Threads (requires jcmd)
if command -v jcmd &> /dev/null; then
    APP_PID=$(cat .app.pid 2>/dev/null || echo "")
    if [ -n "$APP_PID" ] && ps -p $APP_PID > /dev/null 2>&1; then
        THREAD_COUNT=$(jcmd $APP_PID Thread.print | grep "Virtual" | wc -l)
        echo "✅ Virtual threads: $THREAD_COUNT"
    fi
fi

echo ""
echo "✅ Verification complete!"
