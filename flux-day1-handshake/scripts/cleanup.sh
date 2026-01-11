#!/bin/bash
cd "$(dirname "$0")/.."

echo "🧹 Cleaning up..."
mvn clean
pkill -f "FluxGateway" || true
rm -rf target/
echo "✅ Cleanup complete"
