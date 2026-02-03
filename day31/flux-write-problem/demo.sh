#!/bin/bash

set -e

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$SCRIPT_DIR"

echo "🎬 Running Flux Day 31 Demo"
echo "============================"

# Stop any existing server on port 8080
if PID=$(lsof -t -iTCP:8080 -sTCP:LISTEN 2>/dev/null); then
    echo "Stopping existing server (PID $PID)..."
    kill "$PID" 2>/dev/null || true
    sleep 2
fi

if ! docker ps | grep -q flux-postgres; then
    echo "❌ Postgres not running. Starting..."
    docker run --name flux-postgres \
      -e POSTGRES_PASSWORD=flux \
      -e POSTGRES_DB=fluxdb \
      -p 5432:5432 -d postgres:15
    echo "⏳ Waiting for Postgres to be ready..."
    sleep 5
fi

echo "✓ Postgres is running"

mvn clean compile -q
echo "✓ Compilation complete"

echo "🚀 Starting dashboard server..."
mvn exec:exec -Dexec.executable="java" -Dexec.args="--enable-preview -cp %classpath com.flux.FluxApplication" -q &
APP_PID=$!

echo "⏳ Waiting for server to start..."
sleep 3

echo ""
echo "✓ Dashboard running at: http://localhost:8080"
echo ""
echo "📋 Next Steps:"
echo "  1. Open http://localhost:8080 in your browser"
echo "  2. Click 'Run Postgres Benchmark'"
echo "  3. Click 'Run LSM Simulation'"
echo "  4. Compare throughput and GC metrics"
echo ""
echo "Press Ctrl+C to stop..."

wait $APP_PID
