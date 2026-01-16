#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Check for duplicate services
check_duplicate_services() {
    local PIDS=$(pgrep -f "com.flux.gateway.concurrency.Main" 2>/dev/null || true)
    if [ -n "$PIDS" ]; then
        echo "⚠️  Warning: Found running servers with PIDs: $PIDS"
        if [ -t 0 ]; then
            # Interactive terminal - prompt user
            read -p "Do you want to stop them? [y/N]: " answer
            if [[ "$answer" =~ ^[Yy]$ ]]; then
                pkill -f "com.flux.gateway.concurrency.Main" 2>/dev/null || true
                sleep 2
                echo "✅ Stopped existing servers"
            else
                echo "❌ Cannot start new server while others are running"
                exit 1
            fi
        else
            # Non-interactive - auto-stop
            echo "Stopping existing servers (non-interactive mode)..."
            pkill -f "com.flux.gateway.concurrency.Main" 2>/dev/null || true
            sleep 2
            echo "✅ Stopped existing servers"
        fi
    fi
}

check_duplicate_services

echo "🔨 Compiling Flux Day 7 project..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"
echo ""

# Accept server type as command-line argument or prompt for it
if [ $# -ge 1 ]; then
    # Use command-line argument
    case "$1" in
        thread|1) TYPE="thread" ;;
        nio|2) TYPE="nio" ;;
        virtual|3) TYPE="virtual" ;;
        *) 
            echo "❌ Invalid server type: $1"
            echo "Usage: $0 [thread|nio|virtual]"
            echo "   or: $0 [1|2|3]"
            exit 1 
        ;;
    esac
else
    # Interactive mode
    echo "Choose a server to start:"
    echo "  1) Thread-per-Connection"
    echo "  2) NIO Reactor"
    echo "  3) Virtual Threads"
    echo ""
    read -p "Enter choice [1-3]: " choice
    
    case $choice in
        1) TYPE="thread" ;;
        2) TYPE="nio" ;;
        3) TYPE="virtual" ;;
        *) echo "Invalid choice"; exit 1 ;;
    esac
fi

echo ""
echo "🚀 Starting $TYPE server..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.concurrency.Main" -Dexec.args="$TYPE"
