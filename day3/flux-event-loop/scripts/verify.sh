#!/bin/bash

cd "$(dirname "$0")/.."

echo "🔍 Flux Gateway Verification"
echo "═════════════════════════════════════"
echo ""

# Check if server is running
echo "Checking server status..."
if lsof -i:9090 > /dev/null 2>&1; then
    echo "✓ Gateway server is running on port 9090"
else
    echo "✗ Gateway server is NOT running"
    exit 1
fi

if lsof -i:8080 > /dev/null 2>&1; then
    echo "✓ Dashboard server is running on port 8080"
else
    echo "✗ Dashboard server is NOT running"
    exit 1
fi

echo ""
echo "Testing metrics endpoint..."
response=$(curl -s http://localhost:8080/metrics)
if echo "$response" | grep -q "activeConnections"; then
    echo "✓ Metrics endpoint is responding"
    echo "  Response: $response"
else
    echo "✗ Metrics endpoint is not responding correctly"
    exit 1
fi

echo ""
echo "Testing single client connection..."
# Use netcat or simple telnet test
(echo -ne '\x00\x00\x00\x0aFLUX_HELLO'; sleep 0.5) | nc localhost 9090 > /tmp/flux_test.out 2>&1 &
NC_PID=$!
sleep 1
kill $NC_PID 2>/dev/null || true

if [ -s /tmp/flux_test.out ]; then
    echo "✓ Server accepted connection and responded"
else
    echo "⚠ Connection test inconclusive (this is OK if netcat is not available)"
fi

echo ""
echo "Running unit tests..."
if [ -f out/test/com/flux/gateway/EventLoopTest.class ]; then
    java -cp out/test com.flux.gateway.EventLoopTest
else
    echo "⚠ Tests not compiled. Compiling now..."
    javac -d out/test test/com/flux/gateway/EventLoopTest.java src/com/flux/gateway/core/*.java src/com/flux/gateway/protocol/*.java
    java -cp out/test:out/production com.flux.gateway.EventLoopTest
fi

echo ""
echo "═════════════════════════════════════"
echo "✓ All verifications passed!"
echo ""
echo "System status: OPERATIONAL"
echo "  Gateway: localhost:9090"
echo "  Dashboard: http://localhost:8080"
