#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔧 Compiling Flux Typing Indicators..."

# Check for duplicate - port 8080 or existing gateway
if [ -f .gateway.pid ]; then
    OLD_PID=$(cat .gateway.pid)
    if ps -p $OLD_PID > /dev/null 2>&1; then
        echo "⚠️  Gateway already running (PID: $OLD_PID). Run ./cleanup.sh first to stop."
        exit 1
    fi
    rm -f .gateway.pid
fi
if command -v lsof > /dev/null 2>&1 && lsof -i :8080 > /dev/null 2>&1; then
    echo "⚠️  Port 8080 in use. Run ./cleanup.sh or stop the conflicting process."
    exit 1
fi

# Compile with Maven
if command -v mvn &> /dev/null; then
    mvn clean compile -q
else
    echo "❌ Maven not found. Please install Maven 3.9+"
    exit 1
fi

echo "✅ Compilation complete"
echo ""
echo "🚀 Starting Gateway + Dashboard..."

# Run the main application (MAVEN_OPTS needed - exec:java runs in same JVM as Maven)
MAVEN_OPTS="--enable-preview" mvn exec:java -Dexec.mainClass="com.flux.gateway.GatewayMain" -q &

GATEWAY_PID=$!
echo $GATEWAY_PID > .gateway.pid

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Gateway PID: $GATEWAY_PID"
echo "  Dashboard:   http://localhost:8080"
echo "  Logs:        logs/gateway.log"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Press Ctrl+C to stop"

wait $GATEWAY_PID
