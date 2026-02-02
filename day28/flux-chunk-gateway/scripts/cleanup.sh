#!/bin/bash

echo "🧹 Cleaning up Flux Gateway..."

# Kill running processes
if [ -f .gateway.pid ]; then
    kill $(cat .gateway.pid) 2>/dev/null || true
    rm .gateway.pid
fi

if [ -f .dashboard.pid ]; then
    kill $(cat .dashboard.pid) 2>/dev/null || true
    rm .dashboard.pid
fi

# Kill any remaining Java processes on our ports
lsof -ti:9000 | xargs kill -9 2>/dev/null || true
lsof -ti:8080 | xargs kill -9 2>/dev/null || true

# Clean Maven artifacts
mvn clean -q 2>/dev/null || true

# Clean Redis test data
redis-cli del guild:test-guild:members > /dev/null 2>&1 || true

echo "✅ Cleanup complete!"
