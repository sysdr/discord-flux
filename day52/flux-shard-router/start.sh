#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 Starting Flux Shard Router..."

# Free port 8080 and stop any existing instance (avoids "Address already in use")
if [ -f .app.pid ]; then
    PID=$(cat .app.pid)
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "Stopping existing instance (PID: $PID)..."
        kill "$PID" 2>/dev/null
        sleep 2
    fi
    rm -f .app.pid
fi
pkill -f "FluxShardRouterApp" 2>/dev/null || true
pkill -f "exec:java" 2>/dev/null || true
# Kill whatever is still bound to 8080 (Linux/WSL)
if command -v fuser >/dev/null 2>&1; then
    fuser -k 8080/tcp 2>/dev/null && sleep 2 || true
elif command -v lsof >/dev/null 2>&1; then
    lsof -ti :8080 2>/dev/null | xargs -r kill 2>/dev/null && sleep 2 || true
fi

# Compile
echo "📦 Compiling..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"

# Run application
echo "🔧 Starting application..."
mvn exec:java -q &

APP_PID=$!
echo $APP_PID > .app.pid

echo "✅ Application started (PID: $APP_PID)"
echo ""
echo "=============================================="
echo "  📊 Dashboard (from WSL):  http://localhost:8080"
echo "  📊 From Windows browser:   http://127.0.0.1:8080"
WSL_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
[ -n "$WSL_IP" ] && echo "  📊 Or use WSL IP:          http://${WSL_IP}:8080"
echo "=============================================="
echo ""
echo "📋 Logs: logs/app.log"
echo "To stop: ./cleanup.sh"
