#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up Flux Subscriber..."

if [ -f .pid ]; then
    PID=$(cat .pid | grep "Gateway PID" | cut -d: -f2 | tr -d ' ')
    if [ -n "$PID" ]; then
        echo "Stopping Gateway (PID: $PID)..."
        kill $PID 2>/dev/null || true
    fi
    rm -f .pid
fi

# Clean Redis test data if redis-cli exists
if command -v redis-cli >/dev/null 2>&1; then
    echo "Flushing Redis test data..."
    redis-cli --scan --pattern "guild:stream:*" 2>/dev/null | xargs -r redis-cli DEL 2>/dev/null || true
fi

# Clean build artifacts
mvn clean -q 2>/dev/null || true

echo "✓ Cleanup complete"
