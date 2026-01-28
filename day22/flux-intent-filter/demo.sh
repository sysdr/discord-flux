#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "🎬 Running Intent Filter Demo..."
echo ""

if [ -f .gateway.pid ] && kill -0 "$(cat .gateway.pid)" 2>/dev/null; then
    echo "⚠️  Gateway already running. Using existing instance."
else
    echo "📡 Starting gateway in background..."
    "$SCRIPT_DIR/start.sh" &
    echo "   Waiting for gateway to be ready..."
    for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
        if curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/metrics 2>/dev/null | grep -q 200; then
            echo "   Gateway ready."
            break
        fi
        sleep 1
    done
    if ! curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/metrics 2>/dev/null | grep -q 200; then
        echo "❌ Gateway did not become ready in time."
        exit 1
    fi
fi

echo "🔥 Running load test (100 connections, 10k events/sec, 30 seconds)..."
"$SCRIPT_DIR/mvnw" exec:java -Dexec.mainClass="com.flux.gateway.server.LoadTest" -Dexec.args="100 10000 30" -q

echo ""
echo "✅ Demo complete!"
echo "📊 View results at: http://localhost:8081/dashboard"
