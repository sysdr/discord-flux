#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

echo "🧹 Cleaning up..."

# Kill any running Java processes
pkill -f FluxGateway
pkill -f LoadTest

# Clean Maven artifacts
mvn clean -q

# Remove logs
rm -f *.log

echo "✅ Cleanup complete"
