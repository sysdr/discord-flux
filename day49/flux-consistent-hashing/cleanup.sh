#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up Flux Consistent Hashing Ring..."

# Kill running processes
pkill -f "com.flux.gateway.GatewayRouter" 2>/dev/null || true

# Clean Maven build artifacts
mvn clean -q 2>/dev/null || rm -rf target

# Clean logs
rm -rf logs/*.log 2>/dev/null || true

echo "✓ Cleanup complete"
