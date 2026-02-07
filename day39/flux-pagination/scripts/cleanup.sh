#!/bin/bash
echo "🧹 Cleaning up..."

# Kill any running Java processes
pkill -f "FluxPaginationServer" || true

# Clean Maven build artifacts
cd "$(dirname "$0")/.."
mvn clean -q

echo "✅ Cleanup complete"
