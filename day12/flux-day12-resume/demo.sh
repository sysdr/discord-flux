#!/bin/bash

echo "========================================="
echo "Flux Day 12: Resume Capability Demo"
echo "========================================="

# Check if server is running
if ! curl -s http://localhost:8081/api/metrics > /dev/null 2>&1; then
    echo "Error: Gateway server is not running!"
    echo "Please start the server first with: ./start.sh"
    exit 1
fi

echo ""
echo "Running load test with resume simulation..."
echo ""

# Use java directly instead of mvn exec to avoid mainClass override issues
cd "$(dirname "$0")"
java -cp "target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" com.flux.gateway.LoadTester

echo ""
echo "========================================="
echo "Demo complete!"
echo "========================================="
echo "Open http://localhost:8081/dashboard to see metrics"
