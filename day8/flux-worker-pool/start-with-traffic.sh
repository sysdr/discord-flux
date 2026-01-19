#!/bin/bash
# Start server and automatically begin generating traffic

cd "$(dirname "$0")"

echo "🚀 Starting Gateway Server with automatic traffic generation..."

# Start the server
nohup mvn exec:java -Dexec.mainClass="com.flux.gateway.GatewayServer" > server.log 2>&1 &
SERVER_PID=$!

echo "⏳ Waiting for server to start..."
sleep 5

# Check if server is running
if ! pgrep -f "com.flux.gateway.GatewayServer" > /dev/null; then
    echo "❌ Server failed to start. Check server.log"
    exit 1
fi

echo "✅ Server started (PID: $SERVER_PID)"
echo "📊 Dashboard: http://localhost:9090"
echo ""

# Start background traffic generator
echo "🔄 Starting background traffic generator..."
nohup python3 -c "
import socket
import time
import sys

def send_message(msg):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(1)
        s.connect(('localhost', 8080))
        s.sendall(msg.encode())
        s.close()
        return True
    except:
        return False

# Send traffic every 2 seconds
while True:
    # Send a batch of 10 messages
    for i in range(10):
        send_message('PING')
    time.sleep(2)
" > traffic.log 2>&1 &
TRAFFIC_PID=$!

echo "✅ Background traffic generator started (PID: $TRAFFIC_PID)"
echo ""
echo "📈 Metrics will be continuously updated"
echo ""
echo "To stop:"
echo "  pkill -f 'com.flux.gateway.GatewayServer'"
echo "  pkill -f 'traffic generator'"
echo "  Or run: ./cleanup.sh"
