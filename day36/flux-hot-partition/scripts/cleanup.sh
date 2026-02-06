#!/usr/bin/env bash
set -euo pipefail

echo "🧹 Cleaning up Flux Hot Partition project..."

# Kill any running servers
pkill -f "com.flux.server.DashboardServer" 2>/dev/null || true

# Remove build artifacts
rm -rf target/
rm -rf .mvn/

echo "✅ Cleanup complete!"
