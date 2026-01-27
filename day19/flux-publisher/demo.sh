#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🎯 Running Publisher Demo..."
echo ""

# Check if server is running
if ! curl -s http://localhost:8080/dashboard > /dev/null; then
    echo "❌ Server not running. Start it with: $SCRIPT_DIR/start.sh or ./start.sh"
    exit 1
fi

echo "Sending 1000 test messages..."

for i in {1..1000}; do
    guild_id="guild-$((i % 10))"
    channel_id="channel-$((i % 5))"
    user_id="user-$i"
    
    curl -s -X POST http://localhost:8080/messages \
        -H "Content-Type: application/json" \
        -d "{
            \"guild_id\": \"$guild_id\",
            \"channel_id\": \"$channel_id\",
            \"user_id\": \"$user_id\",
            \"content\": \"Demo message $i\"
        }" > /dev/null
    
    if [ $((i % 100)) -eq 0 ]; then
        echo "  Sent $i messages..."
    fi
done

echo ""
echo "✅ Sent 1000 messages successfully!"
echo ""
echo "Check the dashboard: http://localhost:8080/dashboard"
echo "Or verify in Redis:"
echo "  redis-cli XINFO GROUPS guild:guild-0:messages"
