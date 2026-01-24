#!/bin/bash
echo "🧹 Cleaning up..."

# Kill any running Java processes from this project
pkill -f "com.flux.pubsub" 2>/dev/null

# Clean Maven artifacts
mvn clean -q 2>/dev/null

# Remove logs
rm -rf logs/*.log

echo "✓ Cleanup complete"
