#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔍 Verifying Load Test Health..."
echo ""

# Find Java process
PID=$(/usr/bin/jps | grep LoadTestRunner | awk '{print $1}')

if [ -z "$PID" ]; then
    echo "❌ LoadTestRunner not running"
    exit 1
fi

echo "✓ Process found: PID $PID"

# Check Virtual Threads count
VTHREADS=$(/usr/bin/jcmd $PID Thread.print | grep -c "VirtualThread" || echo "0")
echo "✓ Virtual Threads: $VTHREADS"

if [ "$VTHREADS" -lt 100 ]; then
    echo "  ⚠️  Warning: Expected more Virtual Threads"
fi

# Check carrier threads
CARRIERS=$(/usr/bin/jcmd $PID Thread.print | grep -c "CarrierThread" || echo "0")
echo "✓ Carrier Threads: $CARRIERS"

if [ "$CARRIERS" -gt 100 ]; then
    echo "  ⚠️  Warning: Too many carrier threads (should be ~CPU cores)"
fi

# Check heap usage
HEAP=$(/usr/bin/jcmd $PID GC.heap_info | grep "used" | head -1 | awk '{print $3}')
echo "✓ Heap Usage: $HEAP"

# Check file descriptors (Linux only)
if [ -d "/proc/$PID/fd" ]; then
    FD_COUNT=$(ls /proc/$PID/fd | wc -l)
    echo "✓ File Descriptors: $FD_COUNT"
    
    if [ "$FD_COUNT" -gt 60000 ]; then
        echo "  ⚠️  Warning: High FD count (approaching ulimit)"
    fi
fi

# Fetch metrics from dashboard
if command -v curl &> /dev/null; then
    echo ""
    echo "📊 Dashboard Metrics:"
    /usr/bin/curl -s http://localhost:9090/metrics | /usr/bin/python3 -m json.tool 2>/dev/null || true
fi

echo ""
echo "✅ Verification complete!"
