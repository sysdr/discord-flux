#!/bin/bash
set -e

echo "🔨 Compiling project..."
mvn clean compile -q

echo "Starting Flux Gateway..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGatewayMain" -q &
GATEWAY_PID=$!

echo "Gateway PID: $GATEWAY_PID"
echo $GATEWAY_PID > .gateway.pid

echo "⏳ Waiting for gateway to start..."
sleep 3

echo "✅ Gateway running!"
echo "📊 Dashboard: http://localhost:8080"
echo "🔌 WebSocket: ws://localhost:9090/ws?guild=<guildId>"
echo ""
echo "To stop: bash cleanup.sh"
