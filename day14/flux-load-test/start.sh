#!/bin/bash
set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Building project..."
/usr/bin/mvn clean compile -q

echo "🚀 Starting Load Test..."
echo "📊 Dashboard will open at http://localhost:9090"
echo ""

/usr/bin/mvn exec:java -Dexec.mainClass="com.flux.loadtest.runner.LoadTestRunner" \
    -Dexec.args="9090" \
    -q
