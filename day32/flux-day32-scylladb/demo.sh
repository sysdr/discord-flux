#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Running Flux Demo Scenario"

# Check if app is running, start if not
if ! curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/dashboard 2>/dev/null | grep -q 200; then
    echo ""
    echo "Starting application in background..."
    mvn -q exec:java -Dexec.mainClass="com.flux.FluxApplication" &
    APP_PID=$!
    
    echo "Waiting for dashboard to be ready..."
    for i in {1..30}; do
        if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/dashboard 2>/dev/null | grep -q 200; then
            echo "Dashboard ready"
            break
        fi
        sleep 1
    done
    
    if ! curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/dashboard 2>/dev/null | grep -q 200; then
        echo "Dashboard failed to start"
        kill $APP_PID 2>/dev/null || true
        exit 1
    fi
fi

echo ""
echo "Running demo load (10,000 messages via API)..."
curl -s "http://localhost:8080/api/demo?count=10000" > /dev/null

echo "Waiting for demo to complete (inserting messages)..."
sleep 15

echo ""
echo "Demo complete."
echo ""
echo "Dashboard: http://localhost:8080/dashboard (metrics should be updated)"
echo "Run './verify.sh' to inspect database state"
