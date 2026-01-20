#!/bin/bash

# Get the script's directory and change to it
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

echo "Building Flux Session Store..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo "Starting Session Store Server..."
mvn exec:java -Dexec.mainClass="com.flux.session.SessionStoreServer" -q &
SERVER_PID=$!

echo "Server PID: $SERVER_PID"
echo "Dashboard: http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop"

# Wait for server process
wait $SERVER_PID
