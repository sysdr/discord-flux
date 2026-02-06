#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up..."

# Kill any running processes
pkill -f "com.flux.Main" 2>/dev/null || true

# Remove compiled artifacts
rm -rf target/
rm -rf logs/* 2>/dev/null || true

echo "✅ Cleanup complete"
