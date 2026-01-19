#!/bin/bash

# Change to script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up..."

# Kill reactor process
pkill -f "ReactorMain" 2>/dev/null || true

# Remove compiled classes
mvn clean -q 2>/dev/null || true

# Remove logs
rm -f *.log *.jfr

echo "✓ Cleanup complete"
