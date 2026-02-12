#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "==> Building Flux Day 44..."
mvn -q clean package -DskipTests

echo "==> Starting Read State Server on port 8085..."
echo "    Dashboard: http://localhost:8085/dashboard"
echo "    Press Ctrl+C to stop."
echo ""
java -jar target/flux-day44-readstate-1.0-SNAPSHOT.jar
