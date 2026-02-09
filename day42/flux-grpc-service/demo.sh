#!/bin/bash

set -e

# Get script directory (support running from any path)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🎬 Running Flux gRPC Service Demo"
echo "=================================="
echo ""

# Check if server is running
if ! nc -z localhost 9090 2>/dev/null; then
    echo "❌ gRPC server not running on port 9090"
    echo "   Start it with: $SCRIPT_DIR/start.sh"
    exit 1
fi

echo "✅ gRPC server detected on port 9090"
echo ""

# Compile load test client
echo "📦 Compiling load test client..."
mvn compile -q

echo "🔥 Running load test: 1000 messages with 100 concurrent virtual threads..."
echo ""

mvn exec:java -Dexec.mainClass="com.flux.grpc.LoadTestClient" -Dexec.args="1000 100"

echo ""
echo "✅ Demo complete!"
echo "📊 View metrics at: http://localhost:8080"
echo ""
echo "Try these commands:"
echo "  grpcurl -plaintext localhost:9090 list"
echo "  grpcurl -plaintext -d '{\"channel_id\": 123, \"limit\": 10}' localhost:9090 flux.MessageService/StreamMessageHistory"
