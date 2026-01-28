#!/bin/bash

echo "=== Starting Flux Backpressure Gateway ==="

# If ports are in use, stop existing gateway so we can bind
GATEWAY_PORT=9090
DASHBOARD_PORT=8080
if command -v lsof &>/dev/null; then
    if lsof -i :$GATEWAY_PORT -i :$DASHBOARD_PORT -sTCP:LISTEN &>/dev/null; then
        echo "[WARN] Port $GATEWAY_PORT or $DASHBOARD_PORT already in use. Stopping existing gateway..."
        pkill -f "com.flux.backpressure.GatewayServer" 2>/dev/null
        sleep 2
        if lsof -i :$GATEWAY_PORT -i :$DASHBOARD_PORT -sTCP:LISTEN &>/dev/null; then
            echo "[ERROR] Ports still in use. Stop the process manually: pkill -f GatewayServer"
            exit 1
        fi
        echo "[OK] Ports freed."
    fi
fi

# Compile
echo "[BUILD] Compiling with Maven..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "[ERROR] Compilation failed"
    exit 1
fi

echo "[START] Launching GatewayServer..."
mvn exec:java -Dexec.mainClass="com.flux.backpressure.GatewayServer" \
    -Dexec.cleanupDaemonThreads=false &

echo "[INFO] Server starting... Dashboard will be available at http://localhost:8080"
echo "[INFO] Gateway listening on port 9090"
echo "[INFO] Press Ctrl+C to stop"

wait
