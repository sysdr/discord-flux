#!/usr/bin/env bash
set -e

cd "$(dirname "$0")" || exit 1

echo "=========================================="
echo "Flux Gateway Server Startup Script"
echo "=========================================="
echo ""

# Check if project exists
if [ ! -d "flux-zombie-reaper" ]; then
    echo "ERROR: Project directory not found!"
    echo "Please run ./build.sh first"
    exit 1
fi

cd flux-zombie-reaper || exit 1

# Check for Java
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed!"
    exit 1
fi

# Check for Maven
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed!"
    exit 1
fi

# Kill any existing server
echo "Checking for existing server..."
if pgrep -f "FluxGateway" > /dev/null 2>&1; then
    echo "Stopping existing server..."
    pkill -f "FluxGateway" || true
    sleep 2
fi

# Check port 8080
if lsof -i :8080 > /dev/null 2>&1 || netstat -tln 2>/dev/null | grep -q ":8080"; then
    echo "WARNING: Port 8080 is in use!"
    echo "Attempting to free it..."
    pkill -f "FluxGateway" || true
    sleep 2
fi

# Compile
echo ""
echo "Compiling project..."
if ! mvn clean compile -q; then
    echo "ERROR: Compilation failed!"
    echo "Run 'mvn clean compile' to see errors"
    exit 1
fi

echo "✓ Compilation successful"
echo ""

# Start server
echo "Starting Flux Gateway server..."
echo ""
echo "=========================================="
echo "Server will be available at:"
echo "  Dashboard: http://localhost:8080/dashboard"
echo "  Status:    http://localhost:8080/status"
echo "  Metrics:   http://localhost:8080/metrics"
echo "=========================================="
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# Start the server (foreground)
exec mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway"
