#!/bin/bash
set -e

echo "🎬 Running Load Test Demo..."
echo ""
echo "This will:"
echo "  1. Connect 100 WebSocket clients (5 guilds × 20 clients)"
echo "  2. Publish 1000 messages via Redis Streams"
echo "  3. Observe real-time delivery and backpressure"
echo ""
echo "📊 Open http://localhost:8080 to watch metrics"
echo ""
read -p "Press Enter to start..."

mvn test -Dtest=LoadTest -q
