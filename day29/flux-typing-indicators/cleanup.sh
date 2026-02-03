#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up Flux Typing Indicators..."

# Kill gateway if running
if [ -f .gateway.pid ]; then
    PID=$(cat .gateway.pid)
    if ps -p $PID > /dev/null 2>&1; then
        kill $PID
        echo "✅ Stopped Gateway (PID: $PID)"
    fi
    rm .gateway.pid
fi

# Clean Maven artifacts
if [ -d target ]; then
    rm -rf target
    echo "✅ Removed compiled classes"
fi

# Clean logs
if [ -d logs ]; then
    rm -rf logs/*
    echo "✅ Cleared logs"
fi

echo ""
echo "✅ Cleanup complete"
