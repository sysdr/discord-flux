#!/bin/bash
# Start Reactor with proper logging and status checks

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Compiling project..."
mvn clean compile -q

echo "🚀 Starting Reactor..."
mvn exec:java -Dexec.mainClass="com.flux.netpoll.ReactorMain" -q > /tmp/reactor.log 2>&1 &
REACTOR_PID=$!

echo "✓ Reactor started (PID: $REACTOR_PID)"
echo ""
echo "⏳ Waiting for reactor to initialize..."
sleep 10

# Check if it's running
if ps -p $REACTOR_PID > /dev/null 2>&1; then
    echo "✓ Reactor process is running"
else
    echo "✗ Reactor process died. Check /tmp/reactor.log"
    cat /tmp/reactor.log | tail -20
    exit 1
fi

# Check ports
if lsof -i :8080 > /dev/null 2>&1; then
    echo "✓ Port 8080 is listening"
else
    echo "✗ Port 8080 is not listening"
fi

if lsof -i :9090 > /dev/null 2>&1; then
    echo "✓ Port 9090 is listening"
else
    echo "✗ Port 9090 is not listening"
fi

# Test API
if curl -s http://localhost:8080/api/stats > /dev/null 2>&1; then
    echo "✓ Dashboard API is responding"
    echo ""
    echo "📊 Dashboard URLs:"
    echo "   http://localhost:8080/dashboard"
    echo "   http://127.0.0.1:8080/dashboard"
    WSL_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "WSL_IP")
    echo "   http://$WSL_IP:8080/dashboard"
    echo ""
    echo "✅ Reactor is ready!"
    echo "   Process ID: $REACTOR_PID"
    echo "   Log file: /tmp/reactor.log"
else
    echo "✗ Dashboard API is not responding"
    echo "Check /tmp/reactor.log for errors"
fi
