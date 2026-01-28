#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

GATEWAY_PORT=8081
if [ -f .gateway.pid ]; then
    OLD_PID=$(cat .gateway.pid)
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "⚠️  Gateway already running (PID: $OLD_PID). Stop with ./cleanup.sh first."
        exit 1
    fi
    rm -f .gateway.pid
fi
if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -q ":$GATEWAY_PORT "; then
    echo "⚠️  Port $GATEWAY_PORT in use. Stop existing gateway or free the port."
    exit 1
fi

echo "🔨 Building Flux Intent Filter..."
"$SCRIPT_DIR/mvnw" clean compile -q

echo "🚀 Starting Gateway Server..."
"$SCRIPT_DIR/mvnw" exec:java -Dexec.mainClass="com.flux.gateway.server.GatewayServer" -Dexec.args="8081" -q &
GATEWAY_PID=$!
echo $GATEWAY_PID > .gateway.pid

echo "✅ Gateway started (PID: $GATEWAY_PID)"
echo "📊 Dashboard: http://localhost:8081/dashboard"
echo ""
echo "Press Ctrl+C to stop..."

wait $GATEWAY_PID
