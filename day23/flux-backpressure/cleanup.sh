#!/bin/bash

echo "=== Cleaning up Flux Backpressure ==="

# Kill any running processes
echo "[CLEANUP] Stopping running processes..."
pkill -f "GatewayServer" 2>/dev/null
pkill -f "LoadTestClient" 2>/dev/null
pkill -f "mvn exec:java" 2>/dev/null

# Clean Maven artifacts
echo "[CLEANUP] Removing Maven artifacts..."
mvn clean -q 2>/dev/null

# Remove logs
echo "[CLEANUP] Clearing logs..."
rm -f logs/*.log

echo "[CLEANUP] Done!"
