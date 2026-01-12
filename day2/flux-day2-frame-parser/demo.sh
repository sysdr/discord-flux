#!/bin/bash

echo "🎬 Running Flux Frame Parser Demo"
echo ""

# Compile if needed
if [ ! -d "target/classes" ]; then
    echo "Compiling..."
    bash start.sh &
    sleep 2
fi

echo "📡 Sending test frames to the gateway..."
echo ""

# Simple test: use netcat to send a raw WebSocket text frame
# FIN=1, OPCODE=TEXT(0x1), MASKED=1, Length=13, Payload="Hello Flux!"
printf '\x81\x8d\x00\x00\x00\x00Hello Flux!' | nc localhost 9001 &

sleep 1

echo ""
echo "✅ Demo complete. Check the server logs and dashboard at http://localhost:8080"
echo ""
echo "To run a proper load test:"
echo "  javac --enable-preview -source 21 -d target/classes src/test/java/com/flux/gateway/LoadTestClient.java"
echo "  java --enable-preview -cp target/classes com.flux.gateway.LoadTestClient 100 50"
