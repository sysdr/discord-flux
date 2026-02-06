#!/bin/bash
cd "$(dirname "$0")"
echo "🧹 Cleaning up..."

# Kill any Java processes running our simulator
pkill -f "SimulatorServer" 2>/dev/null || true

# Clean Maven build artifacts
mvn -q clean 2>/dev/null || true

# Remove logs
rm -f *.log

echo "✅ Cleanup complete"
