#!/bin/bash

echo "[CLEANUP] Stopping processes..."

pkill -f "com.flux.gateway.GatewayServer" 2>/dev/null
pkill -f "LoadTestClient" 2>/dev/null

echo "[CLEANUP] Removing compiled classes..."
mvn clean -q 2>/dev/null

echo "[CLEANUP] Removing logs..."
rm -f *.log 2>/dev/null

echo "[CLEANUP] Complete"
