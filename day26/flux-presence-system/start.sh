#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Starting Flux Presence System..."

# Check if Redis is running
if ! redis-cli ping > /dev/null 2>&1; then
    echo "Redis is not running!"
    echo "Start Redis with: docker run -d --name redis-presence -p 6379:6379 redis:7-alpine"
    exit 1
fi

echo "Redis connection verified"

# Build project
echo "Building project..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo "Build successful"

# Start application
echo "Starting application..."
mvn exec:java -Dexec.mainClass="com.flux.presence.FluxPresenceApp" -q &

APP_PID=$!
echo $APP_PID > app.pid

sleep 3

if ps -p $APP_PID > /dev/null; then
    echo "Application started (PID: $APP_PID)"
    echo ""
    echo "Dashboard: http://localhost:8080"
    echo "Gateway: localhost:9090"
    echo ""
    echo "Press Ctrl+C to stop"
    wait $APP_PID
else
    echo "Application failed to start"
    exit 1
fi
