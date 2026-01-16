#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

echo "🚀 Starting Flux Gateway..."

# Check if port 8080 is already in use
if command -v lsof >/dev/null 2>&1; then
    if lsof -i :8080 >/dev/null 2>&1; then
        echo "⚠️  Port 8080 is already in use!"
        echo "💡 Run: pkill -f FluxGateway"
        exit 1
    fi
fi

# Compile
echo "🔨 Compiling..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    echo "💡 Try: mvn clean compile"
    exit 1
fi

echo "✅ Compilation successful"
echo ""
echo "🌐 Starting server on port 8080..."
echo "📊 Dashboard: http://localhost:8080/dashboard"
echo "📈 Status: http://localhost:8080/status"
echo "📉 Metrics: http://localhost:8080/metrics"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# Run - remove -q flag to see output
mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway"
