#!/bin/bash
set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🎬 Running Load Test Demo Scenario"
echo "Scenario: Spawn 10,000 clients in waves"
echo ""

# Check if gateway is running
if ! /usr/bin/nc -z localhost 8080 2>/dev/null; then
    echo "❌ Error: Gateway not running on port 8080"
    echo "Please start the gateway first:"
    echo "  cd ../flux-gateway && ./start.sh"
    exit 1
fi

/usr/bin/mvn clean compile -q

echo "Starting load test..."
timeout 120 /usr/bin/mvn exec:java \
    -Dexec.mainClass="com.flux.loadtest.runner.LoadTestRunner" \
    -Dexec.args="9090" \
    -q || true

echo ""
echo "✅ Demo complete!"
echo "Check the dashboard at http://localhost:9090 for results"
