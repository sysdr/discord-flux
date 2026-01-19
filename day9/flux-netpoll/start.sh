#!/bin/bash
set -e

# Change to script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Compiling project..."
mvn clean compile -q

echo "🚀 Starting Reactor..."
mvn exec:java -Dexec.mainClass="com.flux.netpoll.ReactorMain" -q &
REACTOR_PID=$!

echo "✓ Reactor started (PID: $REACTOR_PID)"
echo "📊 Dashboard: http://localhost:8080/dashboard"
echo "🔌 Reactor listening on port 9090"
echo ""
echo "Press Ctrl+C to stop"

wait $REACTOR_PID
