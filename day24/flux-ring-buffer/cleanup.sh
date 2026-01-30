#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up..."

# Kill any running Java processes from this project
pkill -f "com.flux.ringbuffer.Main" 2>/dev/null

# Clean Maven artifacts
mvn clean -q 2>/dev/null

echo "✅ Cleanup complete"
