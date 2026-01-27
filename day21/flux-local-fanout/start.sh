#!/bin/bash
# Run from script directory so paths work when invoked with full path
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Starting Flux Gateway"
echo "=========================================="

# Check if Redis is running
if ! redis-cli ping > /dev/null 2>&1; then
    echo "[ERROR] Redis is not running. Start Redis first:"
    echo "  macOS: brew services start redis"
    echo "  Linux: sudo systemctl start redis"
    exit 1
fi

# Check for duplicate gateway (port 8080 or 9001)
if command -v ss >/dev/null 2>&1; then
    if ss -tlnp 2>/dev/null | grep -q ':8080\|:9001'; then
        echo "[WARN] Port 8080 or 9001 already in use. Stop existing gateway first: bash cleanup.sh"
        exit 1
    fi
elif command -v netstat >/dev/null 2>&1; then
    if netstat -tln 2>/dev/null | grep -q ':8080\|:9001'; then
        echo "[WARN] Port 8080 or 9001 already in use. Stop existing gateway first: bash cleanup.sh"
        exit 1
    fi
fi
if [ -f gateway.pid ] && kill -0 "$(cat gateway.pid)" 2>/dev/null; then
    echo "[WARN] Gateway already running (PID: $(cat gateway.pid)). Stop first: bash cleanup.sh"
    exit 1
fi

echo "[INFO] Compiling project..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "[ERROR] Compilation failed"
    exit 1
fi

echo "[INFO] Starting gateway..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway" -Dexec.args="9001 8080" -q &

GATEWAY_PID=$!
echo $GATEWAY_PID > gateway.pid

echo "[INFO] Gateway PID: $GATEWAY_PID"
echo "[INFO] Dashboard: http://localhost:8080/dashboard.html"
echo "[INFO] Gateway listening on port: 9001"
echo ""
echo "To stop: bash cleanup.sh"
