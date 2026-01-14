#!/bin/bash
set -e

echo "🏗️  Compiling Flux Gateway..."
mvn clean compile -q

echo "🚀 Starting Gateway Server + Dashboard..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGatewayApp" -Dexec.cleanupDaemonThreads=false &
SERVER_PID=$!

echo "Gateway PID: $SERVER_PID" > .flux_pid

echo ""
echo "✅ Server started!"
echo "   Gateway: ws://localhost:8080/gateway"
echo "   Dashboard: http://localhost:8081/dashboard"
echo ""
echo "To stop: ./cleanup.sh"
