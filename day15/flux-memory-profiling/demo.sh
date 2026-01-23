#!/usr/bin/env bash
set -euo pipefail

echo "======================================"
echo "Flux Memory Leak Demo"
echo "======================================"

# Check if gateway is running
if ! nc -z localhost 9000 2>/dev/null; then
    echo "ERROR: Gateway not running on port 9000"
    echo "Please run ./start.sh first"
    exit 1
fi

echo ""
echo "Starting load generator..."
echo "  - 10,000 virtual thread clients"
echo "  - 100 messages per client"
echo "  - 20% will abandon connections (trigger leak)"
echo "  - Will run for 5 minutes"
echo ""
echo "Watch the dashboard at http://localhost:8080/dashboard"
echo ""

mvn exec:java \
    -Dexec.mainClass="com.flux.gateway.LoadGenerator" \
    -Dexec.args="localhost 9000 10000 0.2" \
    -Dexec.classpathScope=compile &

LOAD_PID=$!

echo "Load generator started (PID: $LOAD_PID)"
echo ""
echo "Monitoring for 5 minutes..."
echo "Expected behavior:"
echo "  ✓ Heap grows from 200MB → 1.5GB"
echo "  ✓ Session map contains 40k+ entries (but only 2k active)"
echo "  ✓ DirectBuffer usage grows to 1.2GB"
echo "  ✓ Leak detector reports leaked sentinels"
echo ""

sleep 300

echo ""
echo "Demo complete. Stopping load generator..."
kill $LOAD_PID 2>/dev/null || true

echo ""
echo "Next steps:"
echo "  1. Check /tmp/heap-*.hprof files"
echo "  2. Open in Eclipse MAT: mat.sh /tmp/heap-*.hprof"
echo "  3. Review leak suspects report"
echo "  4. Run OQL queries to find leaked sessions"
echo ""

