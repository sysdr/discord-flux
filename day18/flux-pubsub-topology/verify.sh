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

echo "🧪 Running verification tests..."
echo ""

mvn clean test -q

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ All unit tests passed"
    echo ""
    echo "Running load test for metrics..."
    timeout 30s mvn exec:java -Dexec.mainClass="com.flux.pubsub.LoadTest" -q > /tmp/flux_test_output.txt 2>&1
    
    if grep -q "Guild-centric routing is clearly superior" /tmp/flux_test_output.txt; then
        echo "✓ Load test completed successfully"
        echo ""
        echo "Key Findings:"
        grep "Speed improvement" /tmp/flux_test_output.txt
        grep "Throughput improvement" /tmp/flux_test_output.txt
        grep "Memory reduction" /tmp/flux_test_output.txt
    else
        echo "⚠️  Load test output incomplete or timeout occurred"
        echo "Last 10 lines of output:"
        tail -10 /tmp/flux_test_output.txt
    fi
else
    echo "❌ Tests failed"
    exit 1
fi
