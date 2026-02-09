#!/bin/bash

cd "$(dirname "$0")"

echo "🧹 Cleaning up..."

# Kill Java processes
pkill -f "com.flux.FluxStorageEngine" 2>/dev/null || true

# Remove data
rm -rf data/stcs/*.db data/twcs/*.db logs/*.log

# Remove compiled classes
mvn -q clean

echo "✅ Cleanup complete"
