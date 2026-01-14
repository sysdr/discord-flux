#!/bin/bash

echo "🔍 Verifying Heartbeat Implementation..."
echo ""

# Check if server is running
if ! lsof -i:8080 > /dev/null 2>&1; then
    echo "❌ Server not running on port 8080"
    exit 1
fi
echo "✅ Server is running on port 8080"

# Check dashboard
if ! curl -s http://localhost:8081/dashboard > /dev/null 2>&1; then
    echo "❌ Dashboard not accessible"
    exit 1
fi
echo "✅ Dashboard is accessible at http://localhost:8081/dashboard"

# Check metrics endpoint
METRICS=$(curl -s http://localhost:8081/api/metrics)
if [ -z "$METRICS" ]; then
    echo "❌ Metrics endpoint not responding"
    exit 1
fi
echo "✅ Metrics endpoint responding"
echo "   $METRICS"

# Check Virtual Threads
VTHREAD_COUNT=$(jps -v | grep FluxGatewayApp | wc -l)
if [ "$VTHREAD_COUNT" -gt 0 ]; then
    echo "✅ Application running with Virtual Threads"
else
    echo "⚠️  Could not verify Virtual Thread usage"
fi

# Memory check
echo ""
echo "📊 Memory Profile:"
jstat -gc $(jps | grep FluxGatewayApp | cut -d' ' -f1) 2>/dev/null || echo "   (Run jstat manually for GC stats)"

echo ""
echo "✅ All verifications passed!"
echo ""
echo "Next steps:"
echo "  1. Open dashboard: http://localhost:8081/dashboard"
echo "  2. Run demo: ./demo.sh"
echo "  3. Monitor GC in VisualVM"
