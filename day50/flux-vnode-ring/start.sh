#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🏗️  Building Flux Virtual Node Ring..."

# Compile with Maven
mvn clean compile -q

echo "✓ Build complete"
echo ""
echo "🚀 Starting Flux Gateway..."
echo ""

# Run the application
mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway" -q
