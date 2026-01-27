#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Running demo scenario..."

# Trigger demo on the already-running gateway (avoids "Address already in use")
if ! curl -sf http://localhost:8080/metrics >/dev/null 2>&1; then
    echo "Gateway not running on port 8080. Start it first: ./start.sh"
    exit 1
fi

echo "Triggering demo on gateway (watch dashboard for real-time updates)..."
curl -s -X POST http://localhost:8080/run-demo

echo ""
echo "Demo started. Open http://localhost:8080 and watch metrics update in real time."
