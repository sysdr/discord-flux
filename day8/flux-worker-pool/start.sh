#!/bin/bash
echo "🔨 Compiling project..."
mvn clean compile -q

echo "🚀 Starting Gateway Server..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.GatewayServer" -q &
SERVER_PID=$!

echo "Server PID: $SERVER_PID"
echo "Dashboard: http://localhost:9090"
echo ""
echo "Press Ctrl+C to stop"

wait $SERVER_PID
