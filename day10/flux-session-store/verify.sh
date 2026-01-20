#!/bin/bash

# Get the script's directory and change to it
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

echo "======================================"
echo "Flux Session Store Verification"
echo "======================================"
echo ""

# Run tests
echo "[1/3] Running unit tests..."
mvn test -Dtest=SessionStoreTest -q
if [ $? -eq 0 ]; then
    echo "✓ Unit tests passed"
else
    echo "✗ Unit tests failed"
    exit 1
fi

echo ""
echo "[2/3] Running load test..."
mvn test -Dtest=LoadTest -q > /tmp/loadtest.log 2>&1
if [ $? -eq 0 ]; then
    echo "✓ Load test completed"
    grep "Throughput:" /tmp/loadtest.log
else
    echo "✗ Load test failed"
    cat /tmp/loadtest.log
    exit 1
fi

echo ""
echo "[3/3] Testing server APIs..."

# Check if server is running
if ! curl -s http://localhost:8080/api/metrics > /dev/null 2>&1; then
    echo "⚠ Server not running. Starting server..."
    ./start.sh > /tmp/server.log 2>&1 &
    sleep 5
fi

# Test API
echo "Creating test sessions..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/sessions/create -d "count=100")
CREATED=$(echo $RESPONSE | grep -o '"created":[0-9]*' | cut -d: -f2)

if [ "$CREATED" = "100" ]; then
    echo "✓ API test passed (created $CREATED sessions)"
else
    echo "✗ API test failed"
    exit 1
fi

echo ""
echo "======================================"
echo "All verifications passed!"
echo "======================================"
echo ""
echo "Next steps:"
echo "1. Open http://localhost:8080 to view dashboard"
echo "2. Run './demo.sh load-test' for performance benchmark"
echo "3. Open VisualVM to monitor heap and GC"
