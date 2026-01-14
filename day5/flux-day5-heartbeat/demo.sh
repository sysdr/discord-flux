#!/bin/bash
set -e

echo "🧪 Running Demo: 1000 Clients + Heartbeat Simulation"

mvn compile -q

echo "Starting test clients..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.TestClient" -Dexec.args="1000" &
CLIENT_PID=$!

echo "Client PID: $CLIENT_PID" >> .flux_pid

echo ""
echo "✅ Demo running!"
echo "   - 1000 clients connected"
echo "   - Sending heartbeat ACKs every 25s"
echo "   - View dashboard: http://localhost:8081/dashboard"
echo ""
echo "Press Ctrl+C to stop, then run ./cleanup.sh"
