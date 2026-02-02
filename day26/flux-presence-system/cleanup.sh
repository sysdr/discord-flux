#!/bin/bash

echo "Cleaning up Flux Presence System..."

# Kill application
if [ -f app.pid ]; then
    PID=$(cat app.pid)
    if ps -p $PID > /dev/null 2>&1; then
        kill $PID
        echo "Stopped application (PID: $PID)"
    fi
    rm app.pid
fi

# Clean build artifacts
mvn clean -q > /dev/null 2>&1
echo "Removed build artifacts"

# Clear Redis presence keys (optional)
read -p "Clear Redis presence keys? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    redis-cli --scan --pattern "user:*:presence" | xargs redis-cli DEL > /dev/null 2>&1
    echo "Cleared Redis presence keys"
fi

# Remove logs
rm -f logs/*.log
echo "Cleared logs"

echo "Cleanup complete."
