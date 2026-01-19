#!/bin/bash
# Test script to demonstrate queue depth and rejections

echo "🔥 Testing queue depth and rejections..."
echo "   This will send a burst of messages to fill the queue"
echo ""

cd "$(dirname "$0")"

# Send a large burst of messages very quickly to fill the queue
python3 << 'EOF'
import socket
import time
import threading

def send_message(msg):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(0.5)
        s.connect(('localhost', 8080))
        s.sendall(msg.encode())
        s.close()
        return True
    except:
        return False

# Send messages as fast as possible to fill the queue
print("📤 Sending burst of 5000 messages rapidly...")
threads = []
for i in range(5000):
    t = threading.Thread(target=lambda: send_message(f"MSG{i}"))
    t.start()
    threads.append(t)
    if i % 100 == 0:
        time.sleep(0.01)  # Small delay every 100 messages

# Wait a moment for queue to fill
time.sleep(1)
print("✅ Burst sent. Check dashboard for queue depth!")
print("   Queue capacity is 10,000, so you should see queue depth > 0")
EOF

echo ""
echo "📊 Check the dashboard now - queue depth should be > 0"
echo "   If queue fills completely, you may see rejected tasks"
