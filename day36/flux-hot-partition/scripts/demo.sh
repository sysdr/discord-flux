#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "🎬 Flux Hot Partition Demo"
echo "=========================="
echo ""

# Compile if needed
if [ ! -d "target/classes" ]; then
    echo "Building project..."
    mvn compile -q
fi

echo "Running demo scenarios..."
echo ""

mvn exec:java -Dexec.mainClass="com.flux.simulator.Demo" -q
