#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 Starting Flux Publisher..."

# Check for duplicate service
if pgrep -f "com.flux.publisher.PublisherApp" > /dev/null 2>&1; then
    echo "⚠️  PublisherApp already running. Stop it first with: pkill -f 'com.flux.publisher.PublisherApp'"
    exit 1
fi

# Compile
echo "Compiling..."
mvn clean compile -q

# Run application
echo "Starting server..."
mvn exec:java -Dexec.mainClass="com.flux.publisher.PublisherApp" -q &

# Wait for server to be ready (poll up to 15s)
echo "Waiting for server..."
for i in $(seq 1 15); do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/dashboard 2>/dev/null | grep -q 200; then
        break
    fi
    sleep 1
done

echo ""
echo "✅ Publisher API started!"
echo ""
echo "Dashboard: http://localhost:8080/dashboard"
echo "API Endpoint: POST http://localhost:8080/messages"
echo ""
echo "Press Ctrl+C to stop"

wait
