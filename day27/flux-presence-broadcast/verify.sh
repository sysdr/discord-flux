#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Flux Presence Verification ==="
echo ""

# Find Java process
echo "Checking for running Presence Gateway..."
PID=$(pgrep -f "PresenceGatewayServer" || echo "")

if [ -z "$PID" ]; then
    echo "❌ No running Presence Gateway found"
    echo "   Start the server first with: bash start.sh"
    exit 1
fi

echo "✓ Found Presence Gateway (PID: $PID)"
echo ""

# Check JVM metrics
echo "=== JVM Metrics ==="

# Young Gen allocation rate
echo "1. Young Generation Allocation Rate:"
jstat -gc $PID 1000 3 | tail -n 1 | awk '{
    eden_kb = $6;
    rate_mb = eden_kb / 1024;
    printf "   Eden Space: %.2f MB\n", rate_mb;
    if (rate_mb < 10) {
        print "   ✓ PASS: Allocation rate < 10 MB (zero-allocation goal)";
    } else {
        print "   ❌ FAIL: Allocation rate too high (indicates per-recipient objects)";
    }
}'

echo ""

# GC pause time
echo "2. GC Pause Times:"
jstat -gcutil $PID 1000 3 | tail -n 1 | awk '{
    gc_time = $14;
    printf "   Last GC Time: %.1f ms\n", gc_time;
    if (gc_time < 50) {
        print "   ✓ PASS: GC pause < 50ms";
    } else {
        print "   ❌ FAIL: GC pause too long";
    }
}'

echo ""

# Thread count
echo "3. Virtual Thread Usage:"
THREAD_COUNT=$(jcmd $PID Thread.print | grep -c "VirtualThread" || echo "0")
echo "   Virtual Threads: $THREAD_COUNT"
if [ "$THREAD_COUNT" -gt 100 ]; then
    echo "   ✓ PASS: Using Virtual Threads for fan-out"
else
    echo "   ⚠ WARNING: Low Virtual Thread count"
fi

echo ""

# Check metrics endpoint
echo "4. Application Metrics:"
curl -s http://localhost:8080/metrics | python3 -m json.tool 2>/dev/null || echo "   Dashboard not responding"

echo ""
echo "=== Verification Complete ==="
echo ""
echo "For detailed profiling, use:"
echo "  jconsole $PID    # GUI monitoring"
echo "  visualvm         # Advanced profiling"
echo "  jcmd $PID VM.native_memory summary  # Memory breakdown"
