#!/bin/bash

cd "$(dirname "$0")/.."

echo "🧹 Cleaning up Flux Gateway..."

# Kill running processes
pkill -f "com.flux.gateway.GatewayServer" || true

# Remove compiled classes
rm -rf out/

# Remove logs
rm -rf logs/*.log

# Remove temp files
rm -f /tmp/flux_test.out

echo "✓ Cleanup complete"
