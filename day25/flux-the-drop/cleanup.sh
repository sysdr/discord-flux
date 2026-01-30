#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Cleaning up..."

if [ -f .gateway.pid ]; then
    PID=$(cat .gateway.pid)
    kill $PID 2>/dev/null
    rm .gateway.pid
    echo "Gateway stopped (PID: $PID)"
fi

mvn clean -q 2>/dev/null
rm -rf logs/*.log 2>/dev/null
rm -f /tmp/before.txt /tmp/after.txt 2>/dev/null

echo "Cleanup complete!"
