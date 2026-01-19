#!/bin/bash
# Continuously generate traffic to keep metrics active

echo "🔥 Starting continuous traffic generator..."
echo "   This will send messages every second to keep the dashboard active"
echo "   Press Ctrl+C to stop"
echo ""

while true; do
    # Send a batch of messages
    for i in {1..10}; do
        echo "PING" | timeout 1 nc localhost 8080 > /dev/null 2>&1 &
    done
    sleep 1
done
