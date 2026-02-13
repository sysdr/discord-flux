#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Stop existing app so port 8080 is free (avoid "Address already in use")
if [ -f .app.pid ]; then
    OLD_PID=$(cat .app.pid)
    if ps -p "$OLD_PID" > /dev/null 2>&1; then
        echo "🛑 Stopping existing application (PID: $OLD_PID)..."
        kill "$OLD_PID" 2>/dev/null || true
        sleep 2
    fi
    rm -f .app.pid
fi
if command -v lsof >/dev/null 2>&1; then
    PORT_PID=$(lsof -ti :8080 2>/dev/null) || true
    if [ -n "$PORT_PID" ]; then
        echo "🛑 Freeing port 8080 (PID: $PORT_PID)..."
        kill $PORT_PID 2>/dev/null || true
        sleep 2
    fi
fi

echo "🔨 Compiling Flux Service Discovery..."

# Compile using Maven
mvn clean compile -q

echo "✅ Compilation complete"
echo "🚀 Starting application..."

# Run the application
mvn exec:java -Dexec.mainClass="com.flux.discovery.FluxApplication" -q &

APP_PID=$!
echo $APP_PID > .app.pid

echo "✅ Application started (PID: $APP_PID)"
echo "📊 Dashboard: http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop"

# Wait for application
wait $APP_PID
