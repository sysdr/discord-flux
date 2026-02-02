#!/bin/bash
set -e

echo "📊 Running Chunk Gateway Demo..."
echo "=================================="

# Test 1: Single chunk request
echo "Test 1: Single chunk request (100 members)"
if nc -z localhost 9000 2>/dev/null; then
  (echo '{"op":8,"d":{"guild_id":"test-guild","limit":100,"nonce":"test-1"}}'; sleep 8) | timeout 12 nc localhost 9000 2>/dev/null | head -n 3
else
  echo "⚠️  Gateway not running. Start with ./scripts/start.sh first"
fi

echo ""
echo "Test 2: Large chunk request (1000 members)"
if nc -z localhost 9000 2>/dev/null; then
  (echo '{"op":8,"d":{"guild_id":"test-guild","limit":1000,"nonce":"test-2"}}'; sleep 10) | timeout 15 nc localhost 9000 2>/dev/null | head -n 5
fi

echo ""
echo "Test 3: Query with filter"
if nc -z localhost 9000 2>/dev/null; then
  (echo '{"op":8,"d":{"guild_id":"test-guild","query":"user","limit":50,"nonce":"test-3"}}'; sleep 6) | timeout 10 nc localhost 9000 2>/dev/null | head -n 3
fi

echo ""
echo "✅ Demo complete!"
echo "Open http://localhost:8080 for interactive testing"
