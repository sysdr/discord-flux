#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up Flux Shard Router..."

# Kill running application
if [ -f .app.pid ]; then
    PID=$(cat .app.pid)
    if ps -p $PID > /dev/null 2>&1; then
        echo "Stopping application (PID: $PID)..."
        kill $PID
        sleep 2
    fi
    rm .app.pid
fi

# Kill any remaining Java processes for this project
pkill -f "FluxShardRouterApp" 2>/dev/null

# Clean Maven artifacts
mvn clean -q 2>/dev/null

# Remove logs
rm -rf logs/*.log 2>/dev/null

echo "✅ Cleanup complete"
