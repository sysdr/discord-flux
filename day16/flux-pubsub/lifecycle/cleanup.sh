#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

echo "🧹 Cleaning up..."

# Kill Gateway process
if [ -f "$SCRIPT_DIR/.pids" ]; then
    PID=$(cat "$SCRIPT_DIR/.pids" | grep "Gateway PID" | cut -d: -f2 | tr -d ' ')
    if [ -n "$PID" ]; then
        # Kill the process and its children
        pkill -P $PID 2>/dev/null || true
        kill $PID 2>/dev/null || true
        echo "Killed Gateway (PID: $PID)"
    fi
    rm "$SCRIPT_DIR/.pids"
fi

# Also check for any running Java processes with GatewayServer
pkill -f "com.flux.pubsub.GatewayServer" 2>/dev/null && echo "Killed remaining Gateway processes" || true

# Clean Maven build
mvn clean -q

# Clean Redis streams (optional)
read -p "Delete Redis streams? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    redis-cli DEL guild:1001:events 2>/dev/null || true
    echo "Redis streams deleted"
fi

echo "✅ Cleanup complete"
