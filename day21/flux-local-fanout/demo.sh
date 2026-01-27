#!/bin/bash
# Run from script directory so paths work when invoked with full path
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Flux Demo: Local Fan-Out Broadcast"
echo "=========================================="

# Check if gateway is running
if [ ! -f gateway.pid ]; then
    echo "[ERROR] Gateway not running. Start with: bash start.sh"
    exit 1
fi

echo "[STEP 1] Spawning 100 test connections..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.LoadTest" -Dexec.args="100" -q &
LOADTEST_PID=$!
echo $LOADTEST_PID > loadtest.pid

sleep 5

echo "[STEP 2] Waiting for connections to establish..."
sleep 3

echo "[STEP 3] Publishing 10 broadcast messages to Redis..."
for i in {1..10}; do
    redis-cli PUBLISH guild_events "{\"guildId\":\"guild_001\",\"userId\":\"user_$i\",\"content\":\"Demo message $i\"}" > /dev/null
    echo "  Published message $i"
    sleep 0.5
done

echo ""
echo "=========================================="
echo "Demo Complete!"
echo "=========================================="
echo "Open dashboard: http://localhost:8080/dashboard.html"
echo "Check gateway logs for broadcast metrics"
echo ""
echo "To cleanup: bash cleanup.sh"
