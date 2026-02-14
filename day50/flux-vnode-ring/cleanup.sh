#!/bin/bash
# Comprehensive cleanup: stop services, Docker, remove caches and unused resources

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleanup - Stopping services and removing artifacts"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 1. Stop application services
echo "Stopping Flux Gateway..."
pkill -f "com.flux.gateway.FluxGateway" 2>/dev/null || true
pkill -f "com.flux.gateway" 2>/dev/null || true

# 2. Stop and remove Docker resources
if command -v docker &>/dev/null; then
    CONTAINERS=$(docker ps -aq 2>/dev/null) || true
    if [ -n "$CONTAINERS" ]; then
        echo "Stopping Docker containers..."
        docker stop $CONTAINERS 2>/dev/null || true
        echo "Removing Docker containers..."
        docker rm -f $CONTAINERS 2>/dev/null || true
    else
        echo "No Docker containers to stop"
    fi
    echo "Removing unused Docker images..."
    docker image prune -af 2>/dev/null || true
    echo "Removing unused Docker volumes..."
    docker volume prune -f 2>/dev/null || true
    echo "Removing unused Docker networks..."
    docker network prune -f 2>/dev/null || true
    echo "Removing Docker build cache..."
    docker builder prune -af 2>/dev/null || true
else
    echo "Docker not found, skipping Docker cleanup"
fi

# 3. Remove node_modules, venv, Python caches, Istio
echo "Removing node_modules..."
find "$SCRIPT_DIR" -type d -name "node_modules" 2>/dev/null | while read d; do rm -rf "$d" 2>/dev/null; done || true

echo "Removing Python venv..."
find "$SCRIPT_DIR" -type d \( -name "venv" -o -name ".venv" \) 2>/dev/null | while read d; do rm -rf "$d" 2>/dev/null; done || true

echo "Removing .pytest_cache..."
find "$SCRIPT_DIR" -type d -name ".pytest_cache" 2>/dev/null | while read d; do rm -rf "$d" 2>/dev/null; done || true

echo "Removing .pyc and __pycache__..."
find "$SCRIPT_DIR" -type f -name "*.pyc" -delete 2>/dev/null || true
find "$SCRIPT_DIR" -type d -name "__pycache__" 2>/dev/null | while read d; do rm -rf "$d" 2>/dev/null; done || true

echo "Removing Istio files..."
find "$SCRIPT_DIR" -path "*istio*" -type f -delete 2>/dev/null || true
find "$SCRIPT_DIR" -path "*istio*" -type d 2>/dev/null | sort -r | while read d; do rm -rf "$d" 2>/dev/null; done || true

# 4. Maven cleanup
echo "Cleaning Maven build artifacts..."
mvn clean -q 2>/dev/null || true
find "$SCRIPT_DIR" -type d -name "target" 2>/dev/null | while read d; do rm -rf "$d" 2>/dev/null; done || true

# Remove generated demo files
rm -f src/main/java/com/flux/gateway/Demo.java 2>/dev/null || true

echo ""
echo "✅ Cleanup complete"
