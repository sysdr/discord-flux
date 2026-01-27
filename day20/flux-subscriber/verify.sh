#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔍 Verifying Flux Subscriber..."

# Check if server is running
if ! curl -s http://localhost:8080/metrics > /dev/null 2>&1; then
    echo "❌ Gateway not responding on port 8080"
    exit 1
fi

# Get metrics
METRICS=$(curl -s http://localhost:8080/metrics)

SUBS=$(echo $METRICS | grep -o '"subscriptions":[0-9]*' | cut -d: -f2)
CHURN=$(echo $METRICS | grep -o '"churnCount":[0-9]*' | cut -d: -f2)
DELIVERED=$(echo $METRICS | grep -o '"messagesDelivered":[0-9]*' | cut -d: -f2)
UNROUTABLE=$(echo $METRICS | grep -o '"unroutableMessages":[0-9]*' | cut -d: -f2)

echo ""
echo "📊 Current Metrics:"
echo "  Active subscriptions: $SUBS"
echo "  Churn count: $CHURN"
echo "  Messages delivered: $DELIVERED"
echo "  Unroutable messages: $UNROUTABLE"
echo ""

# Verification checks
if [ -n "$SUBS" ] && [ "$SUBS" -gt 0 ] 2>/dev/null; then
    echo "✓ Subscriptions active"
else
    echo "⚠ No active subscriptions (run demo after starting gateway)"
fi

if [ -n "$DELIVERED" ] && [ "$DELIVERED" -gt 0 ] 2>/dev/null; then
    echo "✓ Messages delivered"
else
    echo "⚠ No messages delivered yet (run ./demo.sh)"
fi

if [ -n "$CHURN" ] && [ "$CHURN" -gt 0 ] 2>/dev/null; then
    echo "✓ Subscription churn tracked"
else
    echo "⚠ No churn yet"
fi

echo ""
echo "View real-time metrics: http://localhost:8080"
