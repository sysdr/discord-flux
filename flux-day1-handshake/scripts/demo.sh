#!/bin/bash
cd "$(dirname "$0")/.."

echo "🎬 Running Demo: 50 concurrent handshakes"
echo "First, make sure the gateway is running (./scripts/start.sh)"
echo "Press Enter to start load test..."
read

mvn test-compile exec:java -Dexec.mainClass="com.flux.gateway.LoadTest" -Dexec.classpathScope=test -Dexec.args="50"
