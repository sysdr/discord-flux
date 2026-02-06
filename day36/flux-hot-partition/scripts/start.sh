#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# Check for duplicate dashboard servers
if pgrep -f "com.flux.server.DashboardServer" > /dev/null; then
    echo "⚠️  Dashboard server already running. Stopping existing instance..."
    pkill -f "com.flux.server.DashboardServer" 2>/dev/null || true
    sleep 2
fi

echo "🔨 Building Flux Hot Partition Simulator..."
mvn clean compile -q

echo "🚀 Starting dashboard server..."
mvn exec:java -Dexec.mainClass="com.flux.server.DashboardServer" -Dexec.args="8080 1" -q &
SERVER_PID=$!

echo "✅ Server started with PID $SERVER_PID"
echo "📊 Dashboard available at http://localhost:8080"
echo ""
echo "Press CTRL+C to stop"

trap "kill $SERVER_PID 2>/dev/null" EXIT
wait $SERVER_PID
