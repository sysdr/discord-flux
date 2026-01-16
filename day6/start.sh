#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

PROJECT_DIR="flux-zombie-reaper"

# Check if server is already running
if command -v pgrep >/dev/null 2>&1; then
    if pgrep -f "FluxGateway" >/dev/null; then
        echo "⚠️  Server is already running!"
        echo "💡 To stop it: pkill -f FluxGateway"
        exit 1
    fi
fi

if command -v lsof >/dev/null 2>&1; then
    if lsof -i :8080 >/dev/null 2>&1; then
        echo "⚠️  Port 8080 is already in use!"
        echo "💡 To stop it: pkill -f FluxGateway"
        exit 1
    fi
fi

# Check if project directory exists
if [ ! -d "$PROJECT_DIR" ]; then
    echo "❌ Project directory '$PROJECT_DIR' not found."
    echo "💡 Run ./build.sh first to build the project"
    exit 1
fi

echo "🚀 Starting Flux Gateway..."

# Change to project directory and run start script
cd "$PROJECT_DIR" || exit 1
exec bash start.sh
