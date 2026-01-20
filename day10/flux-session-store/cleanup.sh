#!/bin/bash

# Get the script's directory and change to it
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

echo "Cleaning up Flux Session Store..."

# Kill server process
pkill -f SessionStoreServer
echo "✓ Stopped server"

# Clean Maven artifacts
mvn clean -q
echo "✓ Cleaned build artifacts"

# Remove logs
rm -f logs/*.log
rm -f /tmp/loadtest.log /tmp/server.log
echo "✓ Removed logs"

echo "Cleanup complete!"
