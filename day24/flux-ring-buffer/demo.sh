#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Ring Buffer Backpressure Demo ==="
echo ""
echo "This demo simulates:"
echo "  - 100 WebSocket clients connected"
echo "  - 10 slow clients (100ms write delay, simulating 3G)"
echo "  - 1000 events/second broadcast to all clients"
echo "  - Ring buffers handling backpressure"
echo ""
echo "Watch the dashboard at http://localhost:8080"
echo "You'll see slow clients' buffers fill up (red cells)"
echo "Fast clients remain green/yellow"
echo ""
echo "Press Ctrl+C to stop"
echo ""

mvn exec:java -Dexec.mainClass="com.flux.ringbuffer.Main" -q
