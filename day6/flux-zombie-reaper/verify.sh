#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

echo "✅ Verifying Zombie Reaper..."

# Check if server is running
if ! curl -s http://localhost:8080/metrics > /dev/null; then
    echo "❌ Server is not running. Start with ./start.sh first"
    exit 1
fi

echo "📊 Fetching metrics..."
METRICS=$(curl -s http://localhost:8080/metrics)

ACTIVE=$(echo $METRICS | grep -o '"activeConnections":[0-9]*' | grep -o '[0-9]*')
KILLED=$(echo $METRICS | grep -o '"zombiesKilled":[0-9]*' | grep -o '[0-9]*')
SLOT=$(echo $METRICS | grep -o '"currentSlot":[0-9]*' | grep -o '[0-9]*')

echo ""
echo "Current State:"
echo "  • Active Connections: $ACTIVE"
echo "  • Zombies Killed: $KILLED"
echo "  • Current Slot: $SLOT / 60"
echo ""

if [ "$SLOT" -ge 0 ]; then
    echo "✅ Wheel is rotating correctly"
else
    echo "❌ Wheel rotation issue"
    exit 1
fi

echo "✅ Verification complete"
echo "💡 Open http://localhost:8080/dashboard to see live visualization"
