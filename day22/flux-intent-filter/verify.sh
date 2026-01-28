#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔍 Verifying Intent Filter Implementation..."
echo ""

# Check if gateway is running
if ! curl -s http://localhost:8081/metrics > /dev/null; then
    echo "❌ Gateway is not running. Start it with ./start.sh first."
    exit 1
fi

echo "✅ Gateway is running"
echo ""

# Fetch metrics
METRICS=$(curl -s http://localhost:8081/metrics)

echo "📊 Current Metrics:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$METRICS" | jq '.' 2>/dev/null || echo "$METRICS"
echo ""

# Extract values; use API's filterRate (filtered/(sent+filtered))
FILTERED=$(echo "$METRICS" | grep -o '"filtered":[0-9]*' | cut -d':' -f2)
PROCESSED=$(echo "$METRICS" | grep -o '"processed":[0-9]*' | cut -d':' -f2)
CONNECTIONS=$(echo "$METRICS" | grep -o '"connections":[0-9]*' | cut -d':' -f2)
FILTER_RATE=$(echo "$METRICS" | grep -o '"filterRate":[0-9.]*' | cut -d':' -f2)
[ -z "$FILTER_RATE" ] && [ -n "$FILTERED" ] && [ -n "$PROCESSED" ] && [ "$PROCESSED" -gt 0 ] && \
    FILTER_RATE=$(awk "BEGIN {printf \"%.2f\", ($FILTERED * 100.0) / $PROCESSED}")

if [ -n "$FILTERED" ] && [ -n "$PROCESSED" ] && [ "$PROCESSED" -gt 0 ]; then
    echo "🎯 Verification Results:"
    echo "   • Connections: $CONNECTIONS"
    echo "   • Events Processed: $PROCESSED"
    echo "   • Events Filtered: $FILTERED"
    echo "   • Filter Rate: $FILTER_RATE%"
    echo ""
    
    if awk "BEGIN {exit ($FILTER_RATE <= 50)}"; then
        echo "✅ PASS: Filter rate >50% (bandwidth optimization working)"
    else
        echo "⚠️  WARNING: Filter rate <50% (check intent configuration)"
    fi
else
    echo "⚠️  Not enough events to verify (run ./demo.sh first)"
fi

echo ""
echo "🧪 Running unit tests..."
"$SCRIPT_DIR/mvnw" test -q

echo ""
echo "✅ Verification complete!"
