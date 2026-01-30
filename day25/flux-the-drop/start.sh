#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
cd "$SCRIPT_DIR"

# Stop existing gateway if ports 8080 or 9090 are in use
stop_existing() {
    local pid=""
    if [ -f .gateway.pid ]; then
        pid=$(cat .gateway.pid)
        if kill -0 "$pid" 2>/dev/null; then
            echo "Stopping existing gateway (PID: $pid)..."
            kill "$pid" 2>/dev/null
            sleep 2
        fi
        rm -f .gateway.pid
    fi
    # If port still in use, find and kill process using it
    if command -v lsof >/dev/null 2>&1; then
        pid=$(lsof -i :8080 -i :9090 -t 2>/dev/null | head -1)
    elif command -v ss >/dev/null 2>&1; then
        pid=$(ss -tlnp 2>/dev/null | grep -E ':8080 |:9090 ' | grep -oP 'pid=\K[0-9]+' | head -1)
    fi
    if [ -n "$pid" ] && [ "$pid" -gt 0 ] 2>/dev/null; then
        echo "Stopping process using ports 8080/9090 (PID: $pid)..."
        kill "$pid" 2>/dev/null
        sleep 2
    fi
}

if ss -tlnp 2>/dev/null | grep -qE ':8080 |:9090 '; then
    stop_existing
fi

echo "Building project..."
mvn clean compile -q

echo "Starting Flux Gateway..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway" &
echo $! > .gateway.pid
echo "Gateway PID: $(cat .gateway.pid)"
echo "Dashboard: http://localhost:8080"
