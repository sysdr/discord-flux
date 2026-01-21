#!/bin/bash

# Check if ports are already in use
check_port() {
    local port=$1
    if lsof -ti :$port >/dev/null 2>&1 || fuser $port/tcp >/dev/null 2>&1; then
        echo "ERROR: Port $port is already in use!"
        echo "Run './cleanup.sh' to stop existing processes, or:"
        echo "  lsof -ti :$port | xargs kill -9"
        exit 1
    fi
}

echo "Checking ports..."
check_port 8080
check_port 8081

echo "Compiling Flux Gateway..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Starting Gateway Server (includes Dashboard)..."
nohup mvn exec:java -Dexec.mainClass="com.flux.gateway.GatewayServer" > server.log 2>&1 &
SERVER_PID=$!

# Save PID for cleanup
echo $SERVER_PID > .server.pid

# Wait for server to start and verify it's listening
echo "Waiting for server to start..."
MAX_ATTEMPTS=30
ATTEMPT=0
STARTED=false

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    sleep 1
    ATTEMPT=$((ATTEMPT + 1))
    
    # Check if process is still running
    if ! kill -0 $SERVER_PID 2>/dev/null; then
        echo ""
        echo "ERROR: Server process died during startup!"
        echo "Check server.log for details:"
        tail -30 server.log
        exit 1
    fi
    
    # Check if ports are listening
    if (lsof -ti :8080 >/dev/null 2>&1) && (lsof -ti :8081 >/dev/null 2>&1); then
        STARTED=true
        break
    fi
done

if [ "$STARTED" = false ]; then
    echo ""
    echo "ERROR: Server failed to start within $MAX_ATTEMPTS seconds!"
    echo "Check server.log for details:"
    tail -30 server.log
    kill $SERVER_PID 2>/dev/null
    rm -f .server.pid
    exit 1
fi

echo ""
echo "========================================="
echo "Flux Gateway is running!"
echo "========================================="
echo "Gateway WebSocket: ws://localhost:8080"
echo "Dashboard: http://localhost:8081/dashboard"
echo ""
echo "Server PID: $SERVER_PID"
echo "Log file: server.log"
echo ""
echo "Press Ctrl+C to stop..."

# Wait for the server process
wait $SERVER_PID
