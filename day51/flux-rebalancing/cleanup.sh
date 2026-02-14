#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up Flux Rebalancing project..."

# Kill any running simulators
pkill -f "com.flux.rebalancing.RebalancingSimulator" 2>/dev/null || true

# Remove compiled files
rm -rf target/
rm -f *.class
rm -f DemoScenario.java DemoScenario.class
rm -f Verify.java Verify.class

echo "✅ Cleanup complete"
