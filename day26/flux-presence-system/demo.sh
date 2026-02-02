#!/bin/bash

echo "Running Presence System Demo..."
echo "===================================="
echo ""

# Check services
if ! redis-cli ping > /dev/null 2>&1; then
    echo "Redis is not running!"
    exit 1
fi

if ! curl -s http://localhost:8080/api/metrics > /dev/null 2>&1; then
    echo "Dashboard is not running!"
    echo "Start with: ./start.sh"
    exit 1
fi

echo "All services running"
echo ""

# Scenario 1: Single user connect
echo "Scenario 1: Single User Connect"
echo "--------------------------------"
curl -s http://localhost:8080/api/simulate-connect | python3 -m json.tool
USER_ID=$(redis-cli --raw GET "user:12345:presence")
echo "Redis verification: user:12345:presence = $USER_ID"
echo ""

sleep 2

# Scenario 2: Thundering herd
echo "Scenario 2: Reconnect Storm (5000 users)"
echo "-----------------------------------------"
START=$(date +%s%3N)
curl -s http://localhost:8080/api/simulate-storm | python3 -m json.tool
END=$(date +%s%3N)
DURATION=$((END - START))
echo "Total time (including network): ${DURATION}ms"
echo ""

sleep 2

# Scenario 2.5: Trigger cache reads and hits (so dashboard shows non-zero L1 hit rate and Redis reads)
echo "Scenario 2.5: Cache Reads (get-presence for user 10000 x 50)"
echo "-------------------------------------------------------------"
for _ in $(seq 1 50); do
    curl -s "http://localhost:8080/api/get-presence?userId=10000" > /dev/null
done
echo "  Done. First call = Redis read (miss), rest = L1 cache hits."
sleep 1
echo ""

# Scenario 3: Metrics snapshot
echo "Scenario 3: Current System Metrics"
echo "-----------------------------------"
curl -s http://localhost:8080/api/metrics | python3 -m json.tool
echo ""

# Scenario 4: Redis key count
echo "Scenario 4: Redis State"
echo "-----------------------"
KEY_COUNT=$(redis-cli DBSIZE | awk '{print $2}')
echo "Total keys in Redis: $KEY_COUNT"
SAMPLE_KEYS=$(redis-cli --scan --pattern "user:*:presence" | head -5)
echo "Sample keys:"
echo "$SAMPLE_KEYS"
echo ""

echo "Demo complete."
echo ""
echo "Next steps:"
echo "  1. Open http://localhost:8080 for interactive dashboard"
echo "  2. Click 'Simulate Reconnect Storm' to see batching in action"
echo "  3. Monitor Redis: redis-cli MONITOR"
echo "  4. Run load test: mvn test -Dtest=LoadTest"
