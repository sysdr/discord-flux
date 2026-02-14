#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🎬 Running Demo Scenario: Node Failure Simulation"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Check if server is running
if ! curl -s http://localhost:8080/stats > /dev/null; then
    echo "❌ Gateway Router is not running. Start it first with ./start.sh"
    exit 1
fi

echo ""
echo "Step 1: Adding 100,000 initial connections..."
curl -s -X POST http://localhost:8080/simulate -d "100000" > /dev/null
sleep 2

echo "✓ Connections distributed across nodes"
echo ""

echo "Step 2: Fetching initial distribution..."
curl -s http://localhost:8080/stats | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8080/stats
echo ""

echo "Step 3: Simulating node failure (removing gateway-node-02)..."
curl -s -X POST http://localhost:8080/removeNode -d "gateway-node-02" > /dev/null
sleep 2

echo "✓ Node removed, connections rebalanced"
echo ""

echo "Step 4: Final distribution (only ~33% relocated)..."
curl -s http://localhost:8080/stats | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8080/stats
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✓ Demo complete! Open dashboard to see visualization:"
echo "  http://localhost:8080/dashboard"
