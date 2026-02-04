#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "Cleaning up Flux environment..."

# Stop running Java processes
pkill -f "flux.FluxApplication" 2>/dev/null || true
pkill -f "flux.LoadTest" 2>/dev/null || true

# Stop Docker containers (this project)
docker compose down -v 2>/dev/null || true

# Stop all Docker containers
docker stop $(docker ps -aq) 2>/dev/null || true

# Remove unused Docker resources
echo "Removing unused Docker resources..."
docker container prune -f 2>/dev/null || true
docker image prune -f 2>/dev/null || true
docker volume prune -f 2>/dev/null || true
docker network prune -f 2>/dev/null || true

# Remove node_modules, venv, .pytest_cache, .pyc, Istio files
echo "Removing build artifacts and caches..."
for dir in node_modules venv .venv .pytest_cache __pycache__; do
    find "$PROJECT_ROOT" -type d -name "$dir" 2>/dev/null | while read -r d; do rm -rf "$d" 2>/dev/null; done || true
done
find "$PROJECT_ROOT" -name "*.pyc" -delete 2>/dev/null || true
find "$PROJECT_ROOT" -name "*istio*" -type f -delete 2>/dev/null || true
find "$PROJECT_ROOT" -type d -name "istio*" 2>/dev/null | while read -r d; do rm -rf "$d" 2>/dev/null; done || true

# Clean Maven artifacts
mvn clean 2>/dev/null || true

# Remove logs
rm -f "$SCRIPT_DIR"/*.log 2>/dev/null || true

echo "Cleanup complete"
