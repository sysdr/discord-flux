#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Compiling Flux Consistent Hashing Ring..."
mvn clean compile -q

echo "🚀 Starting Gateway Router..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.GatewayRouter" -Dexec.args="8080" -q
