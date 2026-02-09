#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🎬 Running Automation Demo Scenarios"
echo ""

if ! curl -s http://localhost:8080/api/workflows > /dev/null 2>&1; then
    echo "❌ Server is not running. Start it with: $SCRIPT_DIR/start.sh"
    exit 1
fi

echo "=== Scenario 1: Single Linear Workflow ==="
echo "Executing linear workflow (3 sequential steps)..."
curl -s -X POST "http://localhost:8080/api/execute?workflowId=linear-workflow"
echo ""
echo ""

sleep 2

echo "=== Scenario 2: Parallel Workflow ==="
echo "Executing parallel workflow (fan-out/fan-in pattern)..."
curl -s -X POST "http://localhost:8080/api/execute?workflowId=parallel-workflow"
echo ""
echo ""

sleep 2

echo "=== Scenario 3: Complex DAG Workflow ==="
echo "Executing complex DAG workflow..."
curl -s -X POST "http://localhost:8080/api/execute?workflowId=complex-workflow"
echo ""
echo ""

echo "=== Scenario 4: Load Test (5 concurrent workflows) ==="
mvn exec:java -Dexec.mainClass="com.flux.automation.LoadTest" -Dexec.args="5" -q

echo ""
echo "✅ Demo Complete!"
echo "📊 View metrics at: http://localhost:8080/dashboard"
