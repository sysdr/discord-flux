#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up Flux Service Discovery..."

# Stop application
if [ -f .app.pid ]; then
    APP_PID=$(cat .app.pid)
    if ps -p $APP_PID > /dev/null 2>&1; then
        echo "🛑 Stopping application (PID: $APP_PID)..."
        kill $APP_PID
        sleep 2
        
        # Force kill if still running
        if ps -p $APP_PID > /dev/null 2>&1; then
            kill -9 $APP_PID
        fi
    fi
    rm .app.pid
fi

# Clean Redis
if command -v redis-cli &> /dev/null && redis-cli ping > /dev/null 2>&1; then
    echo "🧹 Cleaning Redis..."
    redis-cli FLUSHDB > /dev/null
fi

# Clean Maven artifacts
echo "🧹 Cleaning Maven artifacts..."
mvn clean -q > /dev/null 2>&1 || true

# Clean logs
if [ -d logs ]; then
    rm -rf logs/*
fi

echo "✅ Cleanup complete!"
