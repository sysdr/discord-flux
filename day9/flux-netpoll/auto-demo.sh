#!/bin/bash
# Auto-start demo that keeps connections active

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "╔════════════════════════════════════════╗"
echo "║   FLUX NETPOLL - AUTO DEMO            ║"
echo "╚════════════════════════════════════════╝"
echo ""

# Check if reactor is running
if ! pgrep -f "ReactorMain" > /dev/null; then
    echo "⚠️  Reactor is not running!"
    echo "   Starting reactor first..."
    bash start.sh > /tmp/reactor_auto.log 2>&1 &
    sleep 12
    echo "✓ Reactor started"
fi

echo "📊 Dashboard: http://localhost:8080/dashboard"
echo ""
echo "🚀 Starting Active Demo..."
echo "   - 20 connections"
echo "   - 10 minutes duration"
echo "   - Generates events and shows virtual threads"
echo ""
echo "💡 Open the dashboard in your browser to watch metrics update!"
echo ""

# Run the active demo
mvn exec:java -Dexec.mainClass="com.flux.netpoll.ActiveDemoClient" \
    -Dexec.args="20 10" -q

echo ""
echo "✅ Demo complete!"
