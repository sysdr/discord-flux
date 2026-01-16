#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

PROJECT_DIR="flux-zombie-reaper"

echo "🔍 Checking if server is already running..."
if pgrep -f "FluxGateway" > /dev/null; then
    echo "⚠️  Server is already running!"
    echo "💡 To stop it: pkill -f FluxGateway"
    exit 1
fi

if lsof -i :8080 >/dev/null 2>&1 || netstat -tlnp 2>/dev/null | grep -q :8080; then
    echo "⚠️  Port 8080 is already in use!"
    echo "💡 To check: lsof -i :8080"
    exit 1
fi

# Check if project directory exists
if [ ! -d "$PROJECT_DIR" ]; then
    echo "❌ Project directory '$PROJECT_DIR' not found."
    echo "💡 Run ./build.sh first to build the project"
    exit 1
fi

cd "$PROJECT_DIR" || exit 1

echo "🔨 Compiling..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"
echo "🚀 Starting Flux Gateway server..."
echo ""
echo "📊 Dashboard will be available at: http://localhost:8080/dashboard"
echo "📈 Status page: http://localhost:8080/status"
echo "📉 Metrics JSON: http://localhost:8080/metrics"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# Run the server in foreground so user can see output
mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway"
