#!/bin/bash
echo "🔍 Verifying pagination implementation..."

echo "1. Checking initial page load..."
RESPONSE=$(curl -s "http://localhost:8080/messages?channel_id=1&limit=10")
MESSAGE_COUNT=$(echo "$RESPONSE" | grep -o '"messageId"' | wc -l)

if [ "$MESSAGE_COUNT" -gt 0 ]; then
    echo "✅ Initial page loaded: $MESSAGE_COUNT messages"
else
    echo "❌ No messages found"
    exit 1
fi

echo "2. Extracting cursor..."
CURSOR=$(echo "$RESPONSE" | grep -o '"nextCursor":"[^"]*"' | cut -d'"' -f4)

if [ -n "$CURSOR" ]; then
    echo "✅ Cursor extracted: ${CURSOR:0:20}..."
else
    echo "⚠️  No next cursor (possibly last page)"
fi

echo "3. Testing cursor-based pagination..."
RESPONSE2=$(curl -s "http://localhost:8080/messages?channel_id=1&cursor=$CURSOR&limit=10")
MESSAGE_COUNT2=$(echo "$RESPONSE2" | grep -o '"messageId"' | wc -l)

if [ "$MESSAGE_COUNT2" -gt 0 ]; then
    echo "✅ Next page loaded: $MESSAGE_COUNT2 messages"
else
    echo "❌ Cursor pagination failed"
    exit 1
fi

echo "4. Checking stats..."
STATS=$(curl -s "http://localhost:8080/stats")
TOTAL_QUERIES=$(echo "$STATS" | grep -o '"totalQueries":[0-9]*' | cut -d':' -f2)

if [ "$TOTAL_QUERIES" -ge 2 ]; then
    echo "✅ Stats tracking working: $TOTAL_QUERIES queries"
else
    echo "❌ Stats not updating"
    exit 1
fi

echo ""
echo "✅ All verification checks passed!"
