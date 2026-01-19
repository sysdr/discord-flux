#!/bin/bash
set -e

# Change to script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 Flux Netpoll Demo - Generate Active Connections"
echo "📊 Dashboard: http://localhost:8080/dashboard"
echo ""

# Check if reactor is running
if ! pgrep -f "ReactorMain" > /dev/null; then
    echo "⚠️  Reactor is not running!"
    echo "   Start it first with: bash start.sh"
    exit 1
fi

echo "Choose demo type:"
echo "1. Active Demo (20 connections, 5 min) - Shows events & virtual threads"
echo "2. Quick Demo (10 connections, 2 min)"
echo "3. Medium Demo (50 connections, 5 min)"
echo "4. Grid Test (30 connections, 3 min) - Best for connection grid"
read -p "Enter choice [1-4] (default: 1): " choice
choice=${choice:-1}

case $choice in
    1)
        echo ""
        echo "🚀 Running Active Demo..."
        echo "   This will generate events and show virtual threads"
        echo "   Open http://localhost:8080/dashboard to watch metrics update"
        echo ""
        mvn exec:java -Dexec.mainClass="com.flux.netpoll.ActiveDemoClient" \
            -Dexec.args="20 5" -q
        ;;
    2)
        echo "Running Quick Demo (10 connections, 2 min)..."
        mvn exec:java -Dexec.mainClass="com.flux.netpoll.ActiveDemoClient" \
            -Dexec.args="10 2" -q
        ;;
    3)
        echo "Running Medium Demo (50 connections, 5 min)..."
        mvn exec:java -Dexec.mainClass="com.flux.netpoll.ActiveDemoClient" \
            -Dexec.args="50 5" -q
        ;;
    4)
        echo "Running Grid Test (30 connections, 3 min)..."
        mvn exec:java -Dexec.mainClass="com.flux.netpoll.GridTestClient" \
            -Dexec.args="30" -q
        ;;
    *)
        echo "Invalid choice, running Active Demo..."
        mvn exec:java -Dexec.mainClass="com.flux.netpoll.ActiveDemoClient" \
            -Dexec.args="20 5" -q
        ;;
esac
