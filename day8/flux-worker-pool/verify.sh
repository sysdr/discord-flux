#!/bin/bash
echo "🔍 Verifying system health..."
echo ""

# Fetch metrics from dashboard API
METRICS=$(curl -s http://localhost:9090/metrics)

QUEUE=$(echo $METRICS | grep -o '"queueDepth":[0-9]*' | cut -d: -f2)
REJECTED=$(echo $METRICS | grep -o '"rejected":[0-9]*' | cut -d: -f2)
P99=$(echo $METRICS | grep -o '"p99Latency":[0-9]*' | cut -d: -f2)

echo "Queue Depth: $QUEUE"
echo "Rejected Tasks: $REJECTED"
echo "p99 Latency: ${P99}μs"
echo ""

if [ "$QUEUE" -lt 10000 ]; then
    echo "✓ Queue within bounds"
else
    echo "✗ Queue saturated!"
fi

if [ "$REJECTED" -eq 0 ]; then
    echo "✓ No rejections"
else
    echo "⚠ Tasks were rejected"
fi

if [ "$P99" -lt 50000 ]; then
    echo "✓ p99 latency healthy (<50ms)"
else
    echo "⚠ High latency detected"
fi
