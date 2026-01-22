#!/bin/bash

echo "========================================="
echo "Verification Suite - Replay Buffer"
echo "========================================="
echo ""

echo "[TEST] Running unit tests..."
mvn test -q

if [ $? -eq 0 ]; then
    echo "[PASS] All tests passed"
else
    echo "[FAIL] Some tests failed"
    exit 1
fi

echo ""
echo "[VERIFY] Checking for DirectByteBuffer allocation..."
if command -v jcmd &> /dev/null; then
    PID=$(jps | grep GatewayServer | cut -d' ' -f1)
    if [ ! -z "$PID" ]; then
        echo "  Server PID: $PID"
        echo "  Memory usage:"
        jcmd $PID VM.native_memory summary | grep DirectByteBuffer || echo "  (DirectByteBuffer tracking requires -XX:NativeMemoryTracking=summary)"
    else
        echo "  Server not running"
    fi
else
    echo "  jcmd not available"
fi

echo ""
echo "[VERIFY] Complete"
