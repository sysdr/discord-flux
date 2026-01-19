#!/bin/bash
echo "📤 Sending test messages to generate metrics..."

# Send a few test messages
for i in {1..50}; do
    echo "PING" | nc -w 1 localhost 8080 > /dev/null 2>&1 &
    sleep 0.1
done

# Wait a moment for processing
sleep 2

echo "✅ Test messages sent. Check dashboard at http://localhost:9090"
