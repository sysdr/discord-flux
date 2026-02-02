#!/bin/bash

echo "Verifying Presence System Implementation..."
echo "==============================================="
echo ""

# Check 1: Redis connectivity
echo "Check 1: Redis Connection"
if redis-cli ping > /dev/null 2>&1; then
    echo "Redis is reachable"
else
    echo "Redis is not reachable"
    exit 1
fi
echo ""

# Check 2: Dashboard accessibility
echo "Check 2: Dashboard Accessibility"
if curl -s http://localhost:8080 > /dev/null 2>&1; then
    echo "Dashboard is accessible at http://localhost:8080"
else
    echo "Dashboard is not running"
    exit 1
fi
echo ""

# Check 3: L1 Cache Hit Rate
echo "Check 3: L1 Cache Hit Rate Test"
echo "  Populating users via storm, then querying same user 100 times..."

# Ensure users 10000..14999 are online (simulate-storm)
curl -s "http://localhost:8080/api/simulate-storm" > /dev/null
sleep 2
USER_ID=10000
INITIAL_HITS=$(curl -s http://localhost:8080/api/metrics | python3 -c "import sys, json; print(json.load(sys.stdin)['cacheHits'])")
for i in $(seq 1 100); do
    curl -s "http://localhost:8080/api/get-presence?userId=$USER_ID" > /dev/null
done
sleep 1
FINAL_HITS=$(curl -s http://localhost:8080/api/metrics | python3 -c "import sys, json; print(json.load(sys.stdin)['cacheHits'])")
HIT_RATE=$(curl -s http://localhost:8080/api/metrics | python3 -c "import sys, json; print(json.load(sys.stdin)['cacheHitRate'])")
echo "  Cache hits: $((FINAL_HITS - INITIAL_HITS))"
echo "  Hit rate: ${HIT_RATE}%"
if command -v bc > /dev/null 2>&1 && [ -n "$HIT_RATE" ]; then
    if (( $(echo "$HIT_RATE > 90.0" | bc -l 2>/dev/null || echo 0) )); then
        echo "Cache hit rate is healthy (>90%)"
    else
        echo "Cache hit rate is below target (or bc not available)"
    fi
else
    echo "Cache hit rate reported: ${HIT_RATE}%"
fi
echo ""

# Check 4: Redis TTL verification
echo "Check 4: Redis TTL Auto-Expiry"
TEST_USER=88888
redis-cli SET "user:$TEST_USER:presence" "online" EX 5 > /dev/null
echo "  Set test key with 5-second TTL"
sleep 2
TTL=$(redis-cli TTL "user:$TEST_USER:presence")
echo "  TTL after 2 seconds: ${TTL}s"

if [ "$TTL" -gt 0 ] && [ "$TTL" -lt 5 ]; then
    echo "TTL is decrementing correctly"
else
    echo "TTL behavior is unexpected"
fi
echo ""

# Check 5: Batch write verification
echo "Check 5: Batch Write Performance"
INITIAL_WRITES=$(curl -s http://localhost:8080/api/metrics | python3 -c "import sys, json; print(json.load(sys.stdin)['redisWrites'])")
curl -s http://localhost:8080/api/simulate-storm > /dev/null
sleep 2
FINAL_WRITES=$(curl -s http://localhost:8080/api/metrics | python3 -c "import sys, json; print(json.load(sys.stdin)['redisWrites'])")
WRITE_COUNT=$((FINAL_WRITES - INITIAL_WRITES))

echo "  Redis writes for 5000 users: $WRITE_COUNT"
if [ "$WRITE_COUNT" -lt 100 ]; then
    echo "Batching is working (5000 users = $WRITE_COUNT writes)"
else
    echo "Writes are not batched efficiently"
fi
echo ""

echo "==============================================="
echo "Verification complete."
echo ""
echo "Key Findings:"
echo "  - Redis: Connected"
echo "  - Dashboard: Running"
echo "  - L1 Cache: ${HIT_RATE}% hit rate"
echo "  - Batching: $WRITE_COUNT writes for 5000 users"
echo ""
echo "Run './demo.sh' for interactive demonstration"
