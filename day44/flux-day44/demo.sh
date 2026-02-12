#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "==> Building for load test..."
mvn -q clean package -DskipTests

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Flux Day 44 — Write Coalescing Demo"
echo "  500 Virtual Users · 20 seconds"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

java -jar target/flux-day44-readstate-1.0-SNAPSHOT.jar loadtest 500 20
