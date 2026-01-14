#!/bin/bash

cd "$(dirname "$0")"

echo "🧹 Cleaning up Flux Gateway..."

# Kill gateway
if [ -f gateway.pid ]; then
    PID=$(cat gateway.pid)
    if ps -p $PID > /dev/null; then
        kill $PID
        echo "✅ Stopped gateway (PID: $PID)"
    fi
    rm gateway.pid
fi

# Kill any clients
pkill -f ClientSimulator 2>/dev/null

# Clean build artifacts
rm -rf target/
rm -rf logs/*.log

# Docker cleanup
echo ""
echo "🐳 Cleaning up Docker resources..."

# Stop all running containers
if command -v docker &> /dev/null; then
    CONTAINERS=$(docker ps -aq 2>/dev/null)
    if [ ! -z "$CONTAINERS" ]; then
        docker stop $CONTAINERS 2>/dev/null
        echo "✅ Stopped Docker containers"
    fi
    
    # Remove all containers
    docker container prune -f 2>/dev/null
    
    # Remove unused images
    docker image prune -af 2>/dev/null
    
    # Remove unused volumes
    docker volume prune -f 2>/dev/null
    
    # Remove unused networks
    docker network prune -f 2>/dev/null
    
    # Full system cleanup (removes all unused data)
    docker system prune -af --volumes 2>/dev/null
    
    echo "✅ Docker cleanup complete"
else
    echo "⚠️  Docker not found, skipping Docker cleanup"
fi

echo ""
echo "✅ Cleanup complete!"
