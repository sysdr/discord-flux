#!/bin/bash
# Keep-alive traffic generator - runs in background to maintain metrics

echo "🔄 Starting keep-alive traffic generator..."
echo "   This will send periodic messages to keep metrics active"
echo "   Run: pkill -f keep-alive-traffic to stop"

while true; do
    # Send a small batch of messages every 2 seconds
    for i in {1..5}; do
        echo "PING" | timeout 1 nc localhost 8080 > /dev/null 2>&1 &
    done
    sleep 2
done
