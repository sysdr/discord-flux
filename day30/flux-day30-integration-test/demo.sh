#!/bin/bash

echo "🎬 Running Demo Scenario (100 clients, 30 seconds)..."

# Check if Redis is running
if ! redis-cli ping > /dev/null 2>&1; then
    echo "❌ Redis is not running. Start with: redis-server &"
    exit 1
fi

echo "✅ Redis is running"

# Compile
mvn clean compile -q
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

# Run demo: 100 clients, 30 seconds
echo "🚀 Starting demo load test..."
mvn exec:java -Dexec.mainClass="com.flux.integrationtest.IntegrationTestApp" -Dexec.args="100 30" -q

echo "✅ Demo complete!"
