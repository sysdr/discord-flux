#!/bin/bash

echo "=== Flux Backpressure Demo ==="
echo ""
echo "This demo will:"
echo "1. Connect 100 clients to the gateway"
echo "2. Make 10 clients slow (not reading from socket)"
echo "3. Observe backpressure detection and slow consumer eviction"
echo ""
echo "Open http://localhost:8080 in your browser to watch the dashboard."
echo ""
if [ -t 0 ]; then read -p "Press Enter to start..."; fi

# Compile
mvn clean compile -q

# Run load test
echo ""
echo "[DEMO] Starting load test: 100 clients, 10 slow..."
mvn exec:java -Dexec.mainClass="com.flux.backpressure.LoadTestClient" \
    -Dexec.args="100 10" \
    -Dexec.cleanupDaemonThreads=false

