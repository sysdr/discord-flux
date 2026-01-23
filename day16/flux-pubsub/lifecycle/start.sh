#!/bin/bash
set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

echo "🏗️  Compiling project..."
mvn clean compile -q

echo "🚀 Starting Gateway Server..."
nohup mvn exec:java -Dexec.mainClass="com.flux.pubsub.GatewayServer" > gateway.log 2>&1 &
GATEWAY_PID=$!

echo "Gateway PID: $GATEWAY_PID" > "$SCRIPT_DIR/.pids"

echo "✅ Gateway started!"
echo "   Dashboard: http://localhost:8080/dashboard.html"
echo "   Logs: $PROJECT_DIR/gateway.log"
echo ""
echo "To stop: bash $SCRIPT_DIR/cleanup.sh"
