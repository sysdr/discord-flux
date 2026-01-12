#!/bin/bash

echo "🔨 Compiling Flux Gateway..."

# Compile main sources
javac --enable-preview -source 21 \
    -d target/classes \
    -cp ".:src/main/java" \
    $(find src/main/java -name "*.java")

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"
echo ""
echo "🚀 Starting Flux Gateway Server..."
echo "   WebSocket Port: 9001"
echo "   Dashboard: http://localhost:8080"
echo ""

java --enable-preview -cp target/classes com.flux.gateway.GatewayServer 9001
