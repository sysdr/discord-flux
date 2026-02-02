#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

USERS=${1:-5000}
RATE=${2:-50}
DURATION=${3:-60}

echo "=== Flux Presence Load Test ==="
echo "  Users: $USERS"
echo "  Update Rate: $RATE updates/sec"
echo "  Duration: $DURATION seconds"
echo ""

mvn clean compile -q

echo "Starting load test..."
mvn exec:java -Dexec.mainClass="com.flux.presence.LoadTestClient" \
    -Dexec.args="$USERS $RATE $DURATION" \
    -q 2>&1 | grep -v "^\[INFO\]"
