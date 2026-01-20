#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

echo "🔨 Building project (including Protobuf compilation)..."
mvn clean compile

echo "🚀 Starting Flux Serialization Benchmark..."
mvn exec:java -Dexec.mainClass="com.flux.serialization.FluxSerializationApp"
