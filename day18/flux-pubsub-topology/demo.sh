#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

if [ ! -f "pom.xml" ]; then
    echo "❌ Error: pom.xml not found in $SCRIPT_DIR"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven (mvn) not found in PATH"
    exit 1
fi

echo "🔥 Running Topology Comparison Load Test..."
echo ""

mvn clean compile -q
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

mvn exec:java -Dexec.mainClass="com.flux.pubsub.LoadTest" -q
