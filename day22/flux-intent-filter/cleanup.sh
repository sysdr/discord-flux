#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up..."

# Kill gateway if running
if [ -f .gateway.pid ]; then
    GATEWAY_PID=$(cat .gateway.pid)
    if kill -0 "$GATEWAY_PID" 2>/dev/null; then
        echo "Stopping gateway (PID: $GATEWAY_PID)..."
        kill "$GATEWAY_PID" 2>/dev/null || true
    fi
    rm -f .gateway.pid
fi

# Clean build artifacts
"$SCRIPT_DIR/mvnw" clean -q 2>/dev/null || true

echo "✅ Cleanup complete"
