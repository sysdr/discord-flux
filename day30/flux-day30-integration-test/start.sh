#!/bin/bash

echo "🚀 Starting Flux Day 30 Integration Test..."

# Check Redis
if ! redis-cli ping > /dev/null 2>&1; then
    echo "❌ Redis is not running. Please start Redis:"
    echo "   redis-server &"
    exit 1
fi

echo "✅ Redis is running"

# Compile
echo "📦 Compiling..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"

# Run (tee output to logs for verification)
mkdir -p logs
echo "🏃 Starting application..."
mvn exec:java -Dexec.mainClass="com.flux.integrationtest.IntegrationTestApp" -q 2>&1 | tee logs/run.log
