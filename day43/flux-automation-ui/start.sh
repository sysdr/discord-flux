#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Building Flux Automation UI..."
mvn clean compile -q

echo "🚀 Starting Automation Server..."
mvn exec:java -Dexec.mainClass="com.flux.automation.AutomationServer" \
    -Dexec.args="8080" -q &

SERVER_PID=$!
echo $SERVER_PID > .server.pid

echo "✓ Server started (PID: $SERVER_PID)"
echo "📊 Dashboard: http://localhost:8080/dashboard"
echo ""
echo "Press Ctrl+C to stop"

wait $SERVER_PID
