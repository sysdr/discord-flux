#!/bin/bash

echo "🧹 Cleaning up Flux gRPC Service..."

# Kill processes on ports 9090 and 8080
for port in 9090 8080; do
    PID=$(lsof -ti:$port 2>/dev/null)
    if [ ! -z "$PID" ]; then
        echo "   Killing process on port $port (PID: $PID)"
        kill -9 $PID 2>/dev/null || true
    fi
done

# Clean Maven build artifacts
if [ -d "target" ]; then
    echo "   Removing target directory"
    rm -rf target
fi

# Remove generated proto files
if [ -d "src/main/java/com/flux/grpc/proto" ]; then
    echo "   Removing generated proto files"
    rm -rf src/main/java/com/flux/grpc/proto
fi

echo "✅ Cleanup complete"
