#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Stop any existing Flux process to avoid "Address already in use"
pkill -f "flux.FluxApplication" 2>/dev/null || true
sleep 1

echo "Compiling Flux application..."
mvn clean compile -q

echo "Starting Flux application..."
exec mvn exec:java -Dexec.mainClass="com.flux.FluxApplication" -q
