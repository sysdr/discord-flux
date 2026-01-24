#!/bin/bash

echo "🧹 Cleaning up..."

# Kill gateway
if [ -f .gateway.pid ]; then
    PID=$(cat .gateway.pid)
    kill $PID 2>/dev/null || true
    rm .gateway.pid
    echo "✅ Stopped gateway (PID: $PID)"
fi

# Clean build artifacts
mvn clean -q
rm -f hs_err_pid*.log

# Clean Redis test data
redis-cli --scan --pattern "guild:*:messages" | xargs redis-cli del > /dev/null 2>&1 || true

echo "✅ Cleanup complete"
