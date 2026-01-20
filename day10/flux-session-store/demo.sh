#!/bin/bash

# Get the script's directory and change to it
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

case "$1" in
    "load-test")
        echo "Running load test..."
        mvn test -Dtest=LoadTest -q
        ;;
    "cleanup-test")
        echo "Testing cleanup mechanism..."
        echo "1. Creating 10k sessions..."
        curl -s -X POST http://localhost:8080/api/sessions/create -d "count=10000" > /dev/null
        sleep 2
        
        echo "2. Marking 50% as idle..."
        curl -s -X POST http://localhost:8080/api/sessions/mark-idle > /dev/null
        sleep 2
        
        echo "3. Running manual cleanup..."
        RESULT=$(curl -s -X POST http://localhost:8080/api/sessions/cleanup)
        echo "   Result: $RESULT"
        
        echo "4. Check dashboard for updated metrics"
        ;;
    *)
        echo "Usage: $0 {load-test|cleanup-test}"
        echo ""
        echo "  load-test     - Run 100k session load benchmark"
        echo "  cleanup-test  - Test idle session cleanup"
        ;;
esac
