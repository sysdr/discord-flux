#!/bin/bash

cd "$(dirname "$0")"

echo "🔨 Compiling project..."
if command -v mvn &> /dev/null; then
    mvn clean compile -q
else
    echo "Maven not found, using javac..."
    find src/main/java -name "*.java" > sources.txt
    javac --enable-preview --source 21 -d target/classes @sources.txt
    rm sources.txt
fi

echo "🚀 Starting Flux Gateway..."
if command -v mvn &> /dev/null; then
    mvn exec:java -Dexec.mainClass="com.flux.gateway.Main" -q &
else
    java --enable-preview -cp target/classes com.flux.gateway.Main &
fi

GATEWAY_PID=$!
echo $GATEWAY_PID > gateway.pid

echo "✅ Gateway started (PID: $GATEWAY_PID)"
echo "📊 Dashboard: http://localhost:8080"
echo "🔌 Gateway: localhost:9000"
echo ""
echo "Run './demo.sh' to start demo scenario"
