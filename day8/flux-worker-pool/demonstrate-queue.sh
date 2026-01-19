#!/bin/bash
# Demonstrate queue depth and rejections by sending slow messages rapidly

echo "🔥 Demonstrating queue depth and rejections..."
echo "   Sending messages that take 5ms to process..."
echo "   Sending them rapidly to fill the 10,000 capacity queue"
echo ""

python3 << 'EOF'
import socket
import time
import threading
import sys

sent = 0
rejected = 0

def send_slow_message(msg_id):
    global sent, rejected
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(2)
        s.connect(('localhost', 8080))
        # Send a message that will trigger the 5ms sleep (not PING)
        s.sendall(f"SLOW_MSG_{msg_id}".encode())
        s.close()
        sent += 1
        if sent % 500 == 0:
            print(f"  Sent {sent} messages...")
    except Exception as e:
        rejected += 1

# Send messages as fast as possible using threads
print("📤 Sending 15,000 slow messages rapidly (this will fill the 10k queue)...")
threads = []
start_time = time.time()

for i in range(15000):
    t = threading.Thread(target=send_slow_message, args=(i,))
    t.start()
    threads.append(t)
    
    # Don't create too many threads at once
    if len(threads) >= 500:
        for t in threads[:100]:
            t.join(timeout=0.1)
        threads = threads[100:]

# Wait for remaining threads
for t in threads:
    t.join(timeout=1)

elapsed = time.time() - start_time
print(f"✅ Sent {sent} messages in {elapsed:.2f}s")
print(f"   Some may have been rejected if queue filled")
print("")
print("📊 Check dashboard now - you should see:")
print("   - Queue Depth > 0 (messages waiting to be processed)")
print("   - Rejected Tasks > 0 (if queue filled completely)")
EOF

echo ""
echo "⏳ Waiting 3 seconds for metrics to update..."
sleep 3

curl -s http://localhost:9090/metrics | python3 -c "
import json
import sys
data = json.load(sys.stdin)
print('📊 Current Metrics:')
print(f'  Queue Depth: {data[\"queueDepth\"]}')
print(f'  Processed: {data[\"processed\"]}')
print(f'  Rejected: {data[\"rejected\"]}')
print(f'  p50 Latency: {data[\"p50Latency\"]}μs')
print(f'  p99 Latency: {data[\"p99Latency\"]}μs')
"
