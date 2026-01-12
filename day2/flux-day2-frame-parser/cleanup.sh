#!/bin/bash

echo "🧹 Cleaning up Flux project..."

# Kill any running Java processes on ports 9001 and 8080
pkill -f "com.flux.gateway.GatewayServer" 2>/dev/null
pkill -f "com.flux.gateway.LoadTestClient" 2>/dev/null

# Remove compiled classes
rm -rf target/

# Remove logs
rm -rf logs/*.log

echo "✅ Cleanup complete"
