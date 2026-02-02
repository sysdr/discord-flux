#!/bin/bash
set -e

# Run from project root (parent of scripts/)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# Avoid duplicate services: stop any existing dashboard/gateway on our ports
if command -v lsof >/dev/null 2>&1; then
  for port in 8080 9000; do
    pids=$(lsof -ti:$port 2>/dev/null || true)
    if [ -n "$pids" ]; then
      echo "⚠️  Stopping existing process on port $port (PIDs: $pids)"
      echo "$pids" | xargs -r kill -9 2>/dev/null || true
      sleep 2
    fi
  done
fi

echo "🔨 Compiling project..."
mvn clean compile -q

echo "🚀 Starting Dashboard Server..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.server.DashboardServer" > /dev/null 2>&1 &
DASHBOARD_PID=$!
echo "  Dashboard PID: $DASHBOARD_PID"

sleep 2

echo "🚀 Starting Gateway Server..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.server.GatewayServer" &
GATEWAY_PID=$!
echo "  Gateway PID: $GATEWAY_PID"

echo ""
echo "✅ Servers started!"
echo "📊 Dashboard: http://localhost:8080"
echo "🔌 Gateway: ws://localhost:9000"
echo ""
echo "Press Ctrl+C to stop..."

# Save PIDs for cleanup
echo "$DASHBOARD_PID" > .dashboard.pid
echo "$GATEWAY_PID" > .gateway.pid

wait $GATEWAY_PID
