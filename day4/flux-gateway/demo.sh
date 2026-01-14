#!/bin/bash

cd "$(dirname "$0")"

echo "🎬 Starting Demo: 100 Concurrent Clients"
echo "========================================"
echo ""

# Compile client if needed
if [ ! -f "target/test-classes/com/flux/gateway/ClientSimulator.class" ]; then
    echo "Compiling client..."
    if command -v mvn &> /dev/null; then
        mvn test-compile -q
    else
        javac --enable-preview --source 21 -cp "target/classes:target/test-classes" -d target/test-classes \
            src/test/java/com/flux/gateway/ClientSimulator.java
    fi
fi

echo "Spawning 100 clients..."
for i in {1..100}; do
    java --enable-preview -cp "target/classes:target/test-classes" com.flux.gateway.ClientSimulator > /dev/null 2>&1 &
    sleep 0.1
done

echo ""
echo "✅ 100 clients connected!"
echo "📊 Watch metrics at: http://localhost:8080"
echo ""
echo "Press Enter to stop demo and disconnect clients..."
read

echo "🧹 Cleaning up clients..."
pkill -f ClientSimulator

echo "✅ Demo complete!"
