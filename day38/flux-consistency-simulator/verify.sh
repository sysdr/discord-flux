#!/bin/bash
cd "$(dirname "$0")"
echo "✅ Verifying Consistency Behavior..."

echo ""
echo "Test 1: Write with ONE, check all replicas"
echo "==========================================="
curl -s "http://localhost:8080/api/write?level=ONE" | grep -o '"success":[^,]*'
sleep 1
echo "Expected: Fast write (<10ms), may not be on all replicas immediately"

echo ""
echo "Test 2: Write with QUORUM, check latency"
echo "==========================================="
START=$(date +%s%3N)
curl -s "http://localhost:8080/api/write?level=QUORUM" | grep -o '"latency":[^,]*'
END=$(date +%s%3N)
ELAPSED=$((END - START))
echo "Total request time: ${ELAPSED}ms"
echo "Expected: Slower (15-30ms), but guaranteed on 2/3 replicas"

echo ""
echo "Test 3: Simulate partition, try QUORUM"
echo "==========================================="
curl -s "http://localhost:8080/api/partition?enable=true" > /dev/null
sleep 1
curl -s "http://localhost:8080/api/write?level=QUORUM" | grep -o '"success":[^,]*'
echo "Expected: May fail if only 1 healthy replica remains"

echo ""
echo "Test 4: Check metrics"
echo "==========================================="
curl -s "http://localhost:8080/api/metrics" | python3 -m json.tool

echo ""
echo "✅ Verification complete. Check the dashboard at http://localhost:8080"
