#!/bin/bash

echo "========================================="
echo "Verifying Flux Gateway Resume Capability"
echo "========================================="

# Check if server is running
if ! nc -z localhost 8080 2>/dev/null; then
    echo "❌ Gateway server is not running on port 8080"
    echo "   Run ./start.sh first"
    exit 1
fi

echo "✓ Gateway server is running"

# Check if dashboard is accessible
if curl -s http://localhost:8081/dashboard > /dev/null; then
    echo "✓ Dashboard is accessible"
else
    echo "❌ Dashboard is not accessible"
    exit 1
fi

# Check metrics endpoint
METRICS=$(curl -s http://localhost:8081/api/metrics)
if [ -n "$METRICS" ]; then
    echo "✓ Metrics endpoint is working"
    echo "  $METRICS"
else
    echo "❌ Metrics endpoint failed"
    exit 1
fi

# Run unit tests
echo ""
echo "Running unit tests..."
mvn test -q

if [ $? -eq 0 ]; then
    echo "✓ All unit tests passed"
else
    echo "❌ Unit tests failed"
    exit 1
fi

echo ""
echo "========================================="
echo "✓ All verifications passed!"
echo "========================================="
echo ""
echo "Next steps:"
echo "1. Run ./demo.sh to simulate resume scenario"
echo "2. Open http://localhost:8081/dashboard"
echo "3. Monitor resume success rate and latency"
