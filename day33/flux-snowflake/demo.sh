#!/bin/bash

set -e

echo "🧪 Snowflake Demo - Generating IDs via API (updates Dashboard metrics)..."
echo ""

# Check if server is running
if ! curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/id 2>/dev/null | grep -q 200; then
    echo "⚠️  Server not running on port 8080. Start it with: ./start.sh"
    echo "   Falling back to in-process LoadTestRunner..."
    mvn exec:java -Dexec.mainClass="com.flux.loadtest.LoadTestRunner"
    exit 0
fi

echo "Generating 5000 IDs via HTTP API..."
for i in $(seq 1 100); do
    for j in $(seq 1 50); do
        curl -s http://localhost:8080/api/id > /dev/null &
    done
    wait
    echo "  Progress: $((i * 50)) IDs..."
done

echo ""
echo "✅ Demo complete! Check Dashboard: http://localhost:8080/dashboard"
