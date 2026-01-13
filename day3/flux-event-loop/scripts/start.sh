#!/bin/bash
set -e

cd "$(dirname "$0")/.."

echo "🔨 Compiling Flux Gateway..."

# Compile main sources
javac -d out/production \
    src/com/flux/gateway/core/*.java \
    src/com/flux/gateway/protocol/*.java \
    src/com/flux/gateway/dashboard/*.java \
    src/com/flux/gateway/*.java

echo "✓ Compilation complete"
echo ""
echo "🚀 Starting Flux Gateway..."

# Run the server
java -cp out/production com.flux.gateway.GatewayServer
