#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Stop any existing gateway on port 8080 to avoid "Address already in use"
if [ -f .pid ]; then
  OLD_PID=$(grep "Gateway PID" .pid 2>/dev/null | cut -d: -f2 | tr -d ' ')
  if [ -n "$OLD_PID" ]; then
    echo "Stopping previous gateway (PID $OLD_PID)..."
    kill "$OLD_PID" 2>/dev/null || true
    sleep 2
  fi
  rm -f .pid
fi
PORT_PID=""
if command -v lsof >/dev/null 2>&1; then
  PORT_PID=$(lsof -ti :8080 2>/dev/null || true)
elif command -v ss >/dev/null 2>&1; then
  PORT_PID=$(ss -tlnp 2>/dev/null | awk -F',' '/:8080/{print $2}' | grep -o '[0-9]*' | head -1)
fi
if [ -n "$PORT_PID" ]; then
  echo "Freeing port 8080 (PID $PORT_PID)..."
  kill $PORT_PID 2>/dev/null || true
  sleep 2
fi

echo "Building Flux Subscriber..."
mvn clean compile -q

echo "Starting Gateway Server..."
mvn exec:java -Dexec.mainClass="com.flux.subscriber.GatewayServer" -q &
GATEWAY_PID=$!

echo "Gateway PID: $GATEWAY_PID" > .pid

echo "✓ Server started (PID: $GATEWAY_PID)"
echo "✓ Dashboard: http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop"

wait $GATEWAY_PID
