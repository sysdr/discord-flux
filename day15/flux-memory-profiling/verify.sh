#!/usr/bin/env bash
set -euo pipefail

echo "======================================"
echo "Flux Memory Leak Verification"
echo "======================================"

echo ""
echo "[1/4] Checking if gateway is running..."
if nc -z localhost 9000 2>/dev/null; then
    echo "✓ Gateway listening on port 9000"
else
    echo "✗ Gateway not running"
    exit 1
fi

echo ""
echo "[2/4] Checking heap dump files..."
DUMPS=$(ls /tmp/heap-*.hprof 2>/dev/null | wc -l)
if [ "$DUMPS" -gt 0 ]; then
    echo "✓ Found $DUMPS heap dump(s) in /tmp"
    ls -lh /tmp/heap-*.hprof | tail -1
else
    echo "✗ No heap dumps found (may not have triggered yet)"
fi

echo ""
echo "[3/4] Checking JFR recording..."
if [ -f /tmp/flux-profile.jfr ]; then
    SIZE=$(ls -lh /tmp/flux-profile.jfr | awk '{print $5}')
    echo "✓ JFR recording active: $SIZE"
else
    echo "✗ JFR recording not found"
fi

echo ""
echo "[4/4] Fetching current metrics from dashboard..."
METRICS=$(curl -s http://localhost:8080/api/metrics 2>/dev/null || echo "{}")

if [ "$METRICS" != "{}" ]; then
    echo "$METRICS" | python3 -m json.tool 2>/dev/null || echo "$METRICS"
    
    SESSIONS=$(echo "$METRICS" | grep -o '"sessionCount": [0-9]*' | grep -o '[0-9]*')
    DIRECT_MB=$(echo "$METRICS" | grep -o '"directMemoryUsed": [0-9]*' | grep -o '[0-9]*')
    
    if [ ! -z "$SESSIONS" ] && [ "$SESSIONS" -gt 1000 ]; then
        echo ""
        echo "✓ Session count ($SESSIONS) indicates load test ran"
    fi
    
    if [ ! -z "$DIRECT_MB" ]; then
        DIRECT_MB_CALC=$((DIRECT_MB / 1024 / 1024))
        echo "✓ DirectBuffer usage: ${DIRECT_MB_CALC} MB"
    fi
else
    echo "✗ Could not fetch metrics (dashboard may not be running)"
fi

echo ""
echo "======================================"
echo "Verification complete"
echo "======================================"
echo ""
echo "To analyze heap dumps:"
echo "  1. Download Eclipse MAT: https://www.eclipse.org/mat/"
echo "  2. Run: mat.sh /tmp/heap-*.hprof"
echo "  3. Review 'Leak Suspects' report"
echo ""

