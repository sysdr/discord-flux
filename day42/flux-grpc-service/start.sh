#!/bin/bash

set -e

# Get script directory and project root (support running from any path)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔧 Compiling Flux gRPC Service..."

# Compile with Maven
mvn clean compile -q

echo "✅ Compilation complete"
echo "🚀 Starting gRPC server..."

# Run the server
mvn exec:java -Dexec.mainClass="com.flux.grpc.GrpcServer" -q
