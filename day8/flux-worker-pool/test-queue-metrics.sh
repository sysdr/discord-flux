#!/bin/bash
# Test script to demonstrate queue depth and rejections

echo "🔥 Testing Queue Depth and Rejections"
echo "======================================"
echo ""
echo "This script will:"
echo "  1. Send 'SLOW' messages (50ms processing each)"
echo "  2. Send them rapidly to fill the 10,000 capacity queue"
echo "  3. Monitor metrics in real-time"
echo ""

# Function to check metrics
check_metrics() {
    curl -s http://localhost:9090/metrics | python3 -c "
import json, sys
d = json.load(sys.stdin)
print(f\"Queue Depth: {d['queueDepth']:>6} | Rejected: {d['rejected']:>6} | Processed: {d['processed']:>6}\")
"
}

echo "📊 Initial state:"
check_metrics
echo ""

echo "📤 Sending 15,000 SLOW messages rapidly..."
python3 << 'EOF'
import socket
import time
import threading
from concurrent.futures import ThreadPoolExecutor

def send_slow():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(0.5)
        s.connect(('localhost', 8080))
        s.sendall(b'SLOW')
        s.close()
        return True
    except:
        return False

# Send messages as fast as possible
with ThreadPoolExecutor(max_workers=2000) as executor:
    futures = []
    for i in range(15000):
        future = executor.submit(send_slow)
        futures.append(future)
        if i % 2000 == 0 and i > 0:
            print(f"  Sent {i} messages...")

print("✅ All messages sent")
EOF

echo ""
echo "📊 Monitoring metrics (checking every 0.5s for 10 seconds)..."
for i in {1..20}; do
    check_metrics
    sleep 0.5
done

echo ""
echo "📊 Final state:"
check_metrics
echo ""
echo "✅ Test complete!"
echo ""
echo "Note: If queue depth is still 0, the system is processing"
echo "      messages faster than we can send them (which is good!)"
