#!/bin/bash
# Run from script directory so paths work when invoked with full path
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Cleaning up Flux Gateway"
echo "=========================================="

# Kill gateway process by PID file
if [ -f gateway.pid ]; then
    PID=$(cat gateway.pid)
    if kill -0 "$PID" 2>/dev/null; then
        echo "[INFO] Stopping gateway (PID: $PID)..."
        kill "$PID" 2>/dev/null
        sleep 2
        if kill -0 "$PID" 2>/dev/null; then
            echo "[WARN] Force killing gateway..."
            kill -9 "$PID" 2>/dev/null
        fi
    fi
    rm -f gateway.pid
fi

# Kill any remaining FluxGateway or LoadTest Java process (avoid duplicate services)
if command -v pkill >/dev/null 2>&1; then
    if pgrep -f "FluxGateway" >/dev/null 2>&1; then
        echo "[INFO] Stopping any remaining FluxGateway processes..."
        pkill -f "FluxGateway" 2>/dev/null || true
        sleep 1
    fi
    if pgrep -f "com.flux.gateway.LoadTest" >/dev/null 2>&1; then
        echo "[INFO] Stopping any remaining LoadTest processes..."
        pkill -f "com.flux.gateway.LoadTest" 2>/dev/null || true
        sleep 1
    fi
fi

# Kill load test process
if [ -f loadtest.pid ]; then
    PID=$(cat loadtest.pid)
    if ps -p $PID > /dev/null 2>&1; then
        echo "[INFO] Stopping load test (PID: $PID)..."
        kill $PID
    fi
    rm loadtest.pid
fi

# Clean compiled files
echo "[INFO] Cleaning compiled files..."
mvn clean -q

echo "[INFO] Cleanup complete"
