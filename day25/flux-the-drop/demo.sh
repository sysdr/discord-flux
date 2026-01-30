#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Demo: Spawning 100 clients (70 normal, 30 slow)"
echo "=========================================="

mvn test-compile -q

mvn exec:java -Dexec.mainClass="com.flux.gateway.LoadGenerator" \
    -Dexec.classpathScope=test \
    -Dexec.args="70 30"
