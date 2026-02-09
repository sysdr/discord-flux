#!/bin/bash
set -e

echo "🎬 Running TWCS Compaction Demo"
echo "================================"
echo ""

cd "$(dirname "$0")"

# Compile if needed
if [ ! -d "target/classes" ]; then
    echo "📦 Compiling project..."
    mvn -q compile
fi

echo "🔨 Generating test data..."
java -cp "target/classes" com.flux.demo.CompactionDemo

echo ""
echo "✅ Demo complete! Check the dashboard at http://localhost:8080"
