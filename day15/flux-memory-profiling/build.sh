#!/usr/bin/env bash
set -euo pipefail

echo "======================================"
echo "Building Flux Memory Profiling Project"
echo "======================================"

# Clean and compile
echo "[1/2] Compiling with Maven..."
mvn clean compile -q

# Run tests
echo "[2/2] Running tests..."
mvn test -q

echo ""
echo "======================================"
echo "✓ Build successful"
echo "======================================"
echo ""
echo "Next steps:"
echo "  ./start.sh  - Start the gateway"
echo "  ./demo.sh   - Run load test"
echo ""
