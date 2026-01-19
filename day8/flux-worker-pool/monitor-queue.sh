#!/bin/bash
# Monitor queue depth in real-time while sending burst

echo "📊 Monitoring queue depth in real-time..."
echo ""

# Start monitoring in background
(
    for i in {1..30}; do
        METRICS=$(curl -s http://localhost:9090/metrics)
        QUEUE=$(echo $METRICS | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['queueDepth'])")
        REJECTED=$(echo $METRICS | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['rejected'])")
        PROCESSED=$(echo $METRICS | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['processed'])")
        echo "[$(date +%H:%M:%S)] Queue: $QUEUE | Rejected: $REJECTED | Processed: $PROCESSED"
        sleep 0.5
    done
) &
MONITOR_PID=$!

# Send burst of messages
python3 << 'EOF'
import socket
import time
import threading

def send_slow_message(msg_id):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(1)
        s.connect(('localhost', 8080))
        s.sendall(f"BURST_MSG_{msg_id}".encode())
        s.close()
    except:
        pass

print("📤 Sending 20,000 messages as fast as possible...")
threads = []
for i in range(20000):
    t = threading.Thread(target=send_slow_message, args=(i,))
    t.start()
    threads.append(t)
    
    # Limit concurrent threads
    if len(threads) >= 1000:
        for t in threads[:500]:
            t.join(timeout=0.01)
        threads = threads[500:]

for t in threads:
    t.join(timeout=0.1)

print("✅ Burst complete")
EOF

# Wait for monitor to finish
wait $MONITOR_PID

echo ""
echo "📊 Final metrics:"
curl -s http://localhost:9090/metrics | python3 -m json.tool
