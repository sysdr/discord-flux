#!/bin/bash
# Run from script directory so paths work when invoked with full path
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Flux Verification Script"
echo "=========================================="

# Test 1: Redis connectivity
echo "[TEST 1] Checking Redis connectivity..."
if redis-cli ping > /dev/null 2>&1; then
    echo "  ✓ Redis is running"
else
    echo "  ✗ Redis is NOT running"
    exit 1
fi

# Test 2: Gateway running
echo "[TEST 2] Checking if Gateway is running..."
if [ -f gateway.pid ] && ps -p $(cat gateway.pid) > /dev/null 2>&1; then
    echo "  ✓ Gateway is running (PID: $(cat gateway.pid))"
else
    echo "  ✗ Gateway is NOT running"
    exit 1
fi

# Test 3: Dashboard accessible
echo "[TEST 3] Checking Dashboard accessibility..."
if curl -s http://localhost:8080/dashboard.html > /dev/null; then
    echo "  ✓ Dashboard is accessible"
else
    echo "  ✗ Dashboard is NOT accessible"
    exit 1
fi

# Test 4: API endpoint
echo "[TEST 4] Checking API endpoint..."
STATS=$(curl -s http://localhost:8080/api/stats)
if echo "$STATS" | grep -q "activeConnections"; then
    echo "  ✓ API is responding"
    echo "  Active connections: $(echo $STATS | grep -o '"activeConnections":[0-9]*' | cut -d: -f2)"
else
    echo "  ✗ API is NOT responding"
    exit 1
fi

# Test 5: Send test broadcast
echo "[TEST 5] Sending test broadcast..."
redis-cli PUBLISH guild_events '{"guildId":"guild_001","userId":"verify_test","content":"Verification broadcast"}' > /dev/null
if [ $? -eq 0 ]; then
    echo "  ✓ Broadcast sent successfully"
else
    echo "  ✗ Broadcast failed"
    exit 1
fi

echo ""
echo "=========================================="
echo "All verification tests passed!"
echo "=========================================="
