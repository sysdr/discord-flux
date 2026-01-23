#!/bin/bash
set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

MODE="${1:-normal}"

case "$MODE" in
    normal)
        echo "📤 Running normal demo: 100 messages at 10/sec..."
        mvn compile exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath com.flux.pubsub.LoadTestPublisher 1 100"
        ;;
    
    load-test)
        echo "🔥 Running load test: 1000 publishers × 100 messages = 100K total..."
        mvn compile exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath com.flux.pubsub.LoadTestPublisher 1000 100"
        ;;
    
    burst)
        echo "💥 Running burst test: 10 publishers × 1000 messages..."
        mvn compile exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath com.flux.pubsub.LoadTestPublisher 10 1000"
        ;;
    
    *)
        echo "Usage: bash demo.sh [normal|load-test|burst]"
        exit 1
        ;;
esac
