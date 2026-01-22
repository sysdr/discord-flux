#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up..."

# Kill Java processes
/usr/bin/pkill -f LoadTestRunner || true

# Remove compiled classes
rm -rf target/classes
rm -rf target/test-classes

echo "✅ Cleanup complete!"
