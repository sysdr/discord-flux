#!/bin/bash
echo "🧹 Cleaning up..."

# Kill Java processes
pkill -f "com.flux.gateway.GatewayServer"
pkill -f "com.flux.gateway.LoadTest"

# Clean build artifacts
mvn clean -q

echo "✅ Cleanup complete"
