#!/bin/bash

echo "Cleaning up Flux Gateway..."

# Kill server processes by PID file
if [ -f .server.pid ]; then
    PID=$(cat .server.pid)
    if kill -0 "$PID" 2>/dev/null; then
        echo "Killing process $PID..."
        kill "$PID" 2>/dev/null || kill -9 "$PID" 2>/dev/null
    fi
    rm .server.pid
fi

# Kill any Java processes using the gateway ports
echo "Checking for processes on ports 8080 and 8081..."
for port in 8080 8081; do
    PID=$(lsof -ti :$port 2>/dev/null || fuser $port/tcp 2>/dev/null | awk '{print $1}' || echo "")
    if [ -n "$PID" ]; then
        echo "Killing process $PID using port $port..."
        kill "$PID" 2>/dev/null || kill -9 "$PID" 2>/dev/null
    fi
done

# Kill any remaining Java processes matching gateway
pkill -f "com.flux.gateway" 2>/dev/null

# Wait a moment for processes to terminate
sleep 1

# Verify ports are free
for port in 8080 8081; do
    if lsof -ti :$port >/dev/null 2>&1; then
        echo "Warning: Port $port is still in use!"
    else
        echo "Port $port is now free"
    fi
done

# Clean Maven artifacts
mvn clean -q

# Remove logs
rm -rf logs/*.log

echo "Cleanup complete!"
