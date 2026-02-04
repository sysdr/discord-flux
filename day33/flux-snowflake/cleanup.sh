#!/bin/bash

echo "🧹 Cleaning up Flux Snowflake..."

# Kill server if running
if [ -f .server.pid ]; then
    PID=$(cat .server.pid)
    if ps -p $PID > /dev/null 2>&1; then
        echo "  Stopping server (PID: $PID)..."
        kill $PID
    fi
    rm .server.pid
fi

# Clean Maven artifacts
echo "  Removing compiled files..."
mvn clean -q

# Remove logs
if [ -d logs ]; then
    rm -rf logs
fi

echo "✅ Cleanup complete!"
