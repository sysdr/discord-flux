#!/bin/bash

echo "========================================="
echo "Starting Flux Gateway - Replay Buffer"
echo "========================================="
echo ""

echo "[BUILD] Compiling project..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "[ERROR] Compilation failed"
    exit 1
fi

echo "[START] Launching Gateway Server..."
echo ""

mvn exec:java -Dexec.mainClass="com.flux.gateway.GatewayServer" -q
