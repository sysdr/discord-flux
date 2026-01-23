#!/bin/bash
set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🔍 Verifying Redis Streams setup..."

# Check if Redis is running
if ! redis-cli ping > /dev/null 2>&1; then
    echo "❌ Redis is not running!"
    echo "   Start with: docker run -d -p 6379:6379 redis:7-alpine"
    exit 1
fi

echo "✅ Redis is running"

# Check stream exists
STREAM_LEN=$(redis-cli XLEN guild:1001:events 2>/dev/null || echo "0")
echo "✅ Stream 'guild:1001:events' has $STREAM_LEN messages"

# Check consumer group
GROUP_INFO=$(redis-cli XINFO GROUPS guild:1001:events 2>/dev/null || echo "")
if [ -n "$GROUP_INFO" ]; then
    echo "✅ Consumer group 'gateway-consumers' exists"
else
    echo "⚠️  Consumer group not yet created (will be auto-created)"
fi

# Check pending messages
PENDING=$(redis-cli XPENDING guild:1001:events gateway-consumers 2>/dev/null || echo "0")
echo "✅ Pending messages: $PENDING"

# Test dashboard
if curl -s http://localhost:8080/api/metrics > /dev/null; then
    echo "✅ Dashboard API responding"
else
    echo "⚠️  Dashboard not running (start with: bash $SCRIPT_DIR/start.sh)"
fi

echo ""
echo "🎯 Next steps:"
echo "   1. Open: http://localhost:8080/dashboard.html"
echo "   2. Run: bash $SCRIPT_DIR/demo.sh"
echo "   3. Watch metrics update in real-time"
