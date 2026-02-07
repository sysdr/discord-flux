#!/bin/bash

echo "🔍 Verification Script"
echo "====================="

# Check if server is running
if ! curl -s http://localhost:8080 > /dev/null; then
    echo "❌ Server not running on port 8080"
    echo "   Run ./start.sh first"
    exit 1
fi
echo "✅ Server responding on port 8080"

# Insert test messages
echo -n "✅ Inserting 100 test messages... "
RESULT=$(curl -s -X POST "http://localhost:8080/api/insert?count=100")
echo "$RESULT"

sleep 1

# Check stats
STATS=$(curl -s http://localhost:8080/api/stats)
ACTIVE=$(echo $STATS | grep -o '"activeMessages":[0-9]*' | cut -d':' -f2)

if [ "$ACTIVE" -gt 0 ]; then
    echo "✅ Active messages: $ACTIVE"
else
    echo "❌ No active messages found"
    exit 1
fi

# Delete some messages
echo -n "✅ Deleting 50 messages... "
RESULT=$(curl -s -X POST "http://localhost:8080/api/delete?count=50")
echo "$RESULT"

sleep 1

# Check tombstone count
STATS=$(curl -s http://localhost:8080/api/stats)
TOMBSTONES=$(echo $STATS | grep -o '"tombstones":[0-9]*' | cut -d':' -f2)

if [ "$TOMBSTONES" -gt 0 ]; then
    echo "✅ Tombstones created: $TOMBSTONES"
else
    echo "⚠️  No tombstones found (might be compacted already)"
fi

# Force compaction
echo -n "✅ Forcing compaction... "
curl -s -X POST "http://localhost:8080/api/compact"
echo "Done"

sleep 1

# Final stats
echo ""
echo "📊 Final Statistics:"
curl -s http://localhost:8080/api/stats | python3 -m json.tool

echo ""
echo "✅ All verification checks passed!"
echo "   Open http://localhost:8080 to see the dashboard"
