#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🎬 Running Flux Service Discovery Demo..."
echo ""

# Ensure Redis is running
if ! command -v redis-cli &> /dev/null; then
    echo "❌ redis-cli not found. Please install Redis."
    exit 1
fi

if ! redis-cli ping > /dev/null 2>&1; then
    echo "❌ Redis is not running. Start Redis with: docker run -d -p 6379:6379 redis:7-alpine"
    exit 1
fi

echo "✅ Redis is running"
echo ""

# Stop any existing app (avoid duplicate services / port 8080 in use)
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

# Clean Redis
echo "🧹 Cleaning Redis..."
redis-cli FLUSHDB > /dev/null
echo "✅ Redis cleaned"
echo ""

# Compile
echo "🔨 Compiling project..."
mvn clean compile -q
echo "✅ Compilation complete"
echo ""

# Start application in background
echo "🚀 Starting application..."
mvn exec:java -Dexec.mainClass="com.flux.discovery.FluxApplication" > logs/app.log 2>&1 &
APP_PID=$!
echo $APP_PID > .app.pid

# Wait for startup
echo "⏳ Waiting for startup..."
sleep 5

# Check if app is running
if ! ps -p $APP_PID > /dev/null; then
    echo "❌ Application failed to start. Check logs/app.log"
    exit 1
fi

echo "✅ Application started (PID: $APP_PID)"
echo ""

# Demo scenarios
echo "📊 Demo Scenario 1: Initial Registration (10 nodes)"
sleep 3
NODE_COUNT=$(redis-cli KEYS "gateway:nodes:*" | wc -l)
echo "   Active nodes in Redis: $NODE_COUNT"
echo ""

echo "📊 Demo Scenario 2: Registration Storm (100 nodes)"
curl -s -X POST http://localhost:8080/api/simulate/storm > /dev/null
echo "   Storm triggered, waiting for completion..."
sleep 5
NODE_COUNT=$(redis-cli KEYS "gateway:nodes:*" | wc -l)
echo "   Active nodes after storm: $NODE_COUNT"
echo ""

echo "📊 Demo Scenario 3: Simulated Crashes (5 nodes)"
curl -s -X POST http://localhost:8080/api/simulate/crash > /dev/null
sleep 2
NODE_COUNT=$(redis-cli KEYS "gateway:nodes:*" | wc -l)
echo "   Active nodes after crashes: $NODE_COUNT"
echo ""

echo "✅ Demo completed!"
echo ""
echo "📊 Dashboard: http://localhost:8080"
echo "🔍 View logs: tail -f logs/app.log"
echo "🛑 Stop: bash cleanup.sh"
