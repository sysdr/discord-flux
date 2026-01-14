#!/bin/bash

echo "🧹 Cleaning up Flux Gateway..."

if [ -f .flux_pid ]; then
    while read pid; do
        kill $pid 2>/dev/null && echo "Killed process $pid"
    done < .flux_pid
    rm .flux_pid
fi

# Kill any remaining processes
pkill -f "FluxGatewayApp" 2>/dev/null
pkill -f "TestClient" 2>/dev/null

# Clean build artifacts
rm -rf target logs/*.log

echo "✅ Cleanup complete"
