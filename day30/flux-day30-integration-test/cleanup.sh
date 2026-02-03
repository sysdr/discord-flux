#!/bin/bash

echo "🧹 Cleaning up..."

# Kill any running Java processes for this project
pkill -f "com.flux.integrationtest" || true

# Remove compiled classes
rm -rf target/

# Remove logs
rm -rf logs/*.log

echo "✅ Cleanup complete"
