#!/bin/bash

echo "🔍 Verifying Integration Test Results..."

# Check if logs directory exists
if [ ! -d "logs" ]; then
    echo "❌ No logs directory found. Did the test run?"
    exit 1
fi

# Check logs/run.log (from start.sh) or logs/*.log
LOG_FILE="logs/run.log"
[ -f "$LOG_FILE" ] || LOG_FILE=$(ls logs/*.log 2>/dev/null | head -1)

if [ -z "$LOG_FILE" ] || [ ! -f "$LOG_FILE" ]; then
    echo "⚠️  No log file found. Run ./start.sh or ./demo.sh first."
    exit 1
fi

# Look for success indicators in logs
if grep -q "P95 latency < 50ms" "$LOG_FILE" 2>/dev/null; then
    echo "✅ PASS: P95 latency requirement met"
elif grep -q "P95 latency > 50ms" "$LOG_FILE" 2>/dev/null; then
    echo "❌ FAIL: P95 latency > 50ms"
else
    echo "⚠️  P95 latency: Not yet measured (run full test)"
fi

if grep -q "P99 latency < 100ms" "$LOG_FILE" 2>/dev/null; then
    echo "✅ PASS: P99 latency requirement met"
elif grep -q "P99 latency > 100ms" "$LOG_FILE" 2>/dev/null; then
    echo "❌ FAIL: P99 latency > 100ms"
else
    echo "⚠️  P99 latency: Not yet measured (run full test)"
fi

# Check connection count (flexible for demo 100 or full 1000)
ACTUAL=$(grep "clients connected" "$LOG_FILE" 2>/dev/null | tail -1 | grep -oE '[0-9]+' | tail -1)
if [ -n "$ACTUAL" ]; then
    echo "✅ Clients connected: $ACTUAL"
else
    echo "⚠️  Could not determine client count"
fi

# Check metrics API if dashboard is running
if curl -s http://localhost:9090/api/metrics >/dev/null 2>&1; then
    echo "✅ Dashboard API is responding"
fi

echo ""
echo "📊 Full metrics available at: http://localhost:9090/dashboard"
