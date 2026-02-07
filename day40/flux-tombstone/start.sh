#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Compiling..."
mvn clean compile -q

echo "🚀 Starting Flux Tombstone Server..."
mvn exec:java -Dexec.mainClass="com.flux.tombstone.FluxTombstoneServer" -q
