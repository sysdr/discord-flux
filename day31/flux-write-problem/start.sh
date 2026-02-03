#!/bin/bash

set -e

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$SCRIPT_DIR"

# Stop any existing server on port 8080
if PID=$(lsof -t -iTCP:8080 -sTCP:LISTEN 2>/dev/null); then
    echo "Stopping existing server (PID $PID)..."
    kill "$PID" 2>/dev/null || true
    sleep 2
fi

# Start Postgres if not running (required for Postgres benchmark)
if ! lsof -iTCP:5432 -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Starting Postgres (required for Postgres benchmark)..."
    if docker start flux-postgres 2>/dev/null || \
       docker run --name flux-postgres -e POSTGRES_PASSWORD=flux -e POSTGRES_DB=fluxdb -p 5432:5432 -d postgres:15 2>/dev/null; then
        echo "Waiting for Postgres to be ready..."
        sleep 5
    else
        echo "Warning: Could not start Postgres. Postgres benchmark will fail. Run: docker run --name flux-postgres -e POSTGRES_PASSWORD=flux -e POSTGRES_DB=fluxdb -p 5432:5432 -d postgres:15"
    fi
fi

echo "🔨 Compiling Flux Day 31..."
mvn clean compile -q

echo "🚀 Starting application..."
mvn exec:exec -Dexec.executable="java" -Dexec.args="--enable-preview -cp %classpath com.flux.FluxApplication" -q
