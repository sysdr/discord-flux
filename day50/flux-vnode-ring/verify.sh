#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "✅ Verifying Flux Virtual Node Ring"
echo "===================================="
echo ""

# Check if Java 21+ is available
echo "1. Checking Java version..."
java_version=$(java -version 2>&1 | grep version | awk -F '"' '{print $2}' | cut -d'.' -f1)
if [ "$java_version" -ge 21 ]; then
    echo "   ✓ Java $java_version detected"
else
    echo "   ✗ Java 21+ required (found: $java_version)"
    exit 1
fi

echo ""
echo "2. Running unit tests..."
mvn test -q -Dtest=ConsistentHashRingTest,LoadTest

echo ""
echo "3. Checking dashboard availability..."
if curl -s http://localhost:8080 > /dev/null 2>&1; then
    echo "   ✓ Dashboard is running at http://localhost:8080"
else
    echo "   ℹ️  Dashboard not running. Start with: ./start.sh"
fi

echo ""
echo "✅ Verification complete!"
