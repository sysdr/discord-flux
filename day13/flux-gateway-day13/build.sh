#!/bin/bash

echo "========================================="
echo "Building Flux Gateway - Replay Buffer"
echo "========================================="
echo ""

echo "[BUILD] Compiling project..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "[ERROR] Build failed"
    exit 1
fi

echo ""
echo "[SUCCESS] Build completed successfully!"
echo ""
