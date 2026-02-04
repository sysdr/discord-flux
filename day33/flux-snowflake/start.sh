#!/bin/bash

set -e

echo "🔨 Compiling Flux Snowflake Generator..."
mvn clean compile -q

echo "🚀 Starting Snowflake Server..."
mvn exec:java -Dexec.mainClass="com.flux.dashboard.SnowflakeServer" &

SERVER_PID=$!
echo $SERVER_PID > .server.pid

echo "✅ Server started with PID: $SERVER_PID"
echo "📊 Dashboard: http://localhost:8080/dashboard"
echo ""
echo "Press Ctrl+C to stop the server"

wait $SERVER_PID
