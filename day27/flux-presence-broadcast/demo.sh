#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Flux Presence Broadcasting Demo ==="
echo ""
echo "This demo simulates a 1,000 member guild with 20 presence updates/sec"
echo "and demonstrates zero-allocation broadcasting with Virtual Threads."
echo ""

# Compile
echo "Building project..."
mvn clean compile -q

# Start server in background
echo "Starting server..."
mvn exec:java -Dexec.mainClass="com.flux.presence.PresenceGatewayServer" \
    -Dexec.args="1000 20" \
    -q > /tmp/flux-presence-gateway.log 2>&1 &

SERVER_PID=$!
echo "  Server PID: $SERVER_PID"

# Wait for server to start
sleep 5

echo ""
echo "✓ Server running"
echo "  Dashboard: http://localhost:8080/dashboard"
echo "  Guild Size: 1000 members"
echo "  Expected Fan-out: ~20,000 messages/sec"
echo ""
echo "Open the dashboard to watch real-time metrics!"
echo ""
echo "The demo will run for 60 seconds..."
echo "Press Ctrl+C to stop early."
echo ""

# Wait 60 seconds or until Ctrl+C
sleep 60 || true

# Cleanup
echo ""
echo "Stopping server..."
kill $SERVER_PID 2>/dev/null || true
pkill -f "PresenceGatewayServer" 2>/dev/null || true

echo "✓ Demo complete"
