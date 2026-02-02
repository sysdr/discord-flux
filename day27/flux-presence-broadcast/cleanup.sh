#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Flux Cleanup ==="

# Kill any running processes
pkill -f "PresenceGatewayServer" || true
pkill -f "LoadTestClient" || true

# Clean Maven artifacts
mvn clean -q 2>/dev/null || true

# Remove logs
rm -f logs/*.log
rm -f /tmp/flux-*.log

echo "✓ Cleanup complete"
