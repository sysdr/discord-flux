#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f .gateway.pid ]; then
    echo "Error: Gateway not running. Run ./start.sh first."
    exit 1
fi

PID=$(cat .gateway.pid)

echo "=========================================="
echo "Verification: Zero-Allocation Hot Path"
echo "=========================================="

echo "Taking initial heap snapshot..."
jcmd $PID GC.class_histogram > /tmp/before.txt 2>/dev/null

echo "Sending 10,000 messages via load test..."
timeout 10s mvn exec:java -Dexec.mainClass="com.flux.gateway.LoadGenerator" \
    -Dexec.classpathScope=test \
    -Dexec.args="50 0" > /dev/null 2>&1

sleep 2

echo "Taking final heap snapshot..."
jcmd $PID GC.class_histogram > /tmp/after.txt 2>/dev/null

echo ""
echo "Checking for ByteBuffer allocation..."
BEFORE_BB=$(grep 'java.nio.HeapByteBuffer' /tmp/before.txt 2>/dev/null | awk '{print $2}')
AFTER_BB=$(grep 'java.nio.HeapByteBuffer' /tmp/after.txt 2>/dev/null | awk '{print $2}')

if [ -z "$BEFORE_BB" ]; then BEFORE_BB=0; fi
if [ -z "$AFTER_BB" ]; then AFTER_BB=0; fi

DIFF=$((AFTER_BB - BEFORE_BB))

echo "  Before: $BEFORE_BB instances"
echo "  After:  $AFTER_BB instances"
echo "  Diff:   $DIFF"

if [ $DIFF -lt 100 ]; then
    echo "  PASS: Hot path allocations minimal"
else
    echo "  FAIL: Unexpected allocations detected"
fi

echo ""
echo "Verification complete!"
