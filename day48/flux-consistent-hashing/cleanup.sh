#!/bin/bash

echo "=== Cleaning up Flux Consistent Hashing Demo ==="

# Kill any running Java processes from this project
pkill -f "ConsistentHashingDemo" 2>/dev/null || true

# Remove compiled files
rm -rf target
rm -rf .idea
rm -f *.class

echo "✓ Cleanup complete"
