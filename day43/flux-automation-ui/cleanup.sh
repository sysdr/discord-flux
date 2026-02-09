#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up Automation UI..."

if [ -f .server.pid ]; then
    PID=$(cat .server.pid)
    if ps -p $PID > /dev/null 2>&1; then
        echo "Stopping server (PID: $PID)..."
        kill $PID 2>/dev/null || true
    fi
    rm .server.pid
fi

mvn clean -q 2>/dev/null || true
rm -rf logs/*.log 2>/dev/null || true
rm -rf workflows/*.log 2>/dev/null || true

echo "✓ Cleanup complete"
