#!/bin/bash
set -e

cd "$(dirname "$0")/.."

echo "🎬 Flux Gateway Demo - Day 3"
echo "═════════════════════════════════════"
echo ""

# Ensure server is compiled
if [ ! -d "out/production" ]; then
    echo "⚠ Server not compiled. Run ./scripts/start.sh first"
    exit 1
fi

echo "Step 1: Checking if server is running..."
if ! lsof -i:9090 > /dev/null 2>&1; then
    echo "❌ Server not running on port 9090"
    echo "Please run: ./scripts/start.sh"
    exit 1
fi
echo "✓ Server is running"
echo ""

echo "Step 2: Compiling load generator..."
javac -d out/test -cp out/production test/com/flux/gateway/*.java
echo "✓ Load generator ready"
echo ""

echo "Step 3: Running load test (100 connections, 10 messages each)..."
echo ""
java -cp out/test:out/production com.flux.gateway.LoadGenerator 100 10

echo ""
echo "═════════════════════════════════════"
echo "✓ Demo complete!"
echo ""
echo "Next steps:"
echo "  1. Open http://localhost:8080 to see the dashboard"
echo "  2. Watch the metrics update in real-time"
echo "  3. Try: java -cp out/test com.flux.gateway.LoadGenerator 1000 5"
