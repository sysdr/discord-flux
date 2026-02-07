#!/bin/bash

echo "🧹 Cleaning up..."

# Kill any Java processes running FluxTombstoneServer
pkill -f FluxTombstoneServer || true

# Clean Maven artifacts
mvn clean -q 2>/dev/null || true

# Remove logs
rm -f *.log

echo "✅ Cleanup complete"
