#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Building Flux Presence Gateway ==="
mvn clean compile -q

echo ""
echo "=== Starting Presence Gateway Server ==="
echo "  Default Guild: 1000 members"
echo "  Update Rate: 10 updates/sec"
echo ""

mvn exec:java -Dexec.mainClass="com.flux.presence.PresenceGatewayServer" \
    -Dexec.args="1000 10" \
    -q 2>&1 | grep -v "^\[INFO\]"
