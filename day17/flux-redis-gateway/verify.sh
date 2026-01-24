#!/bin/bash

echo "🔍 Verification Checklist"
echo ""

# Check if gateway is running
if [ -f .gateway.pid ]; then
    PID=$(cat .gateway.pid)
    if ps -p $PID > /dev/null; then
        echo "✅ Gateway is running (PID: $PID)"
    else
        echo "❌ Gateway not running"
        exit 1
    fi
else
    echo "❌ Gateway PID file not found"
    exit 1
fi

# Check dashboard
echo -n "✅ Dashboard responding... "
curl -s http://localhost:8080/metrics > /dev/null && echo "OK" || echo "FAIL"

# Check Redis connection
echo -n "✅ Redis available... "
redis-cli ping > /dev/null 2>&1 && echo "OK" || echo "FAIL (start Redis with: redis-server)"

# Check Virtual Thread usage
echo ""
echo "📊 Virtual Thread Count:"
jcmd $PID Thread.print | grep -c "virtual" || echo "0"

echo ""
echo "🧪 To run load test: bash demo.sh"
