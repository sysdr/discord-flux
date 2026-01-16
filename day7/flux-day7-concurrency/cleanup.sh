#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up Flux Day 7 workspace..."
echo ""

# 1. Stop all Java services
echo "1. Stopping Java services..."
PIDS=$(pgrep -f "com.flux.gateway.concurrency.Main" 2>/dev/null || true)
if [ -n "$PIDS" ]; then
    pkill -f "com.flux.gateway.concurrency.Main" 2>/dev/null || true
    sleep 1
    echo "   ✅ Stopped Java services (PIDs: $PIDS)"
else
    echo "   ✅ No Java services running"
fi
echo ""

# 2. Stop Docker containers and cleanup
echo "2. Cleaning up Docker resources..."
if command -v docker &> /dev/null; then
    # Stop all running containers
    RUNNING_CONTAINERS=$(docker ps -q 2>/dev/null || true)
    if [ -n "$RUNNING_CONTAINERS" ]; then
        echo "   Stopping running containers..."
        docker stop $RUNNING_CONTAINERS 2>/dev/null || true
        echo "   ✅ Stopped running containers"
    fi
    
    # Remove all stopped containers
    STOPPED_CONTAINERS=$(docker ps -a -q 2>/dev/null || true)
    if [ -n "$STOPPED_CONTAINERS" ]; then
        echo "   Removing stopped containers..."
        docker rm $STOPPED_CONTAINERS 2>/dev/null || true
        echo "   ✅ Removed stopped containers"
    fi
    
    # Remove unused images
    echo "   Removing unused Docker images..."
    docker image prune -af 2>/dev/null || true
    echo "   ✅ Cleaned unused images"
    
    # Remove unused volumes
    echo "   Removing unused Docker volumes..."
    docker volume prune -f 2>/dev/null || true
    echo "   ✅ Cleaned unused volumes"
    
    # Remove unused networks
    echo "   Removing unused Docker networks..."
    docker network prune -f 2>/dev/null || true
    echo "   ✅ Cleaned unused networks"
    
    # System prune (optional - aggressive cleanup)
    echo "   Running Docker system prune..."
    docker system prune -af --volumes 2>/dev/null || true
    echo "   ✅ Docker system cleanup complete"
else
    echo "   ⚠️  Docker not found, skipping Docker cleanup"
fi
echo ""

# 3. Remove Maven build artifacts
echo "3. Removing Maven build artifacts..."
if [ -d "target" ]; then
    rm -rf target
    echo "   ✅ Removed target directory"
else
    echo "   ✅ No target directory found"
fi
echo ""

# 4. Remove Python artifacts
echo "4. Removing Python artifacts..."
PYTHON_CLEANED=0

# Remove venv directories
VENV_DIRS=$(find . -type d -name "venv" -o -name ".venv" -o -name "env" 2>/dev/null || true)
if [ -n "$VENV_DIRS" ]; then
    echo "$VENV_DIRS" | while read -r dir; do
        rm -rf "$dir"
        echo "   ✅ Removed: $dir"
    done
    PYTHON_CLEANED=1
fi

# Remove .pytest_cache
PYTEST_CACHES=$(find . -type d -name ".pytest_cache" 2>/dev/null || true)
if [ -n "$PYTEST_CACHES" ]; then
    echo "$PYTEST_CACHES" | while read -r dir; do
        rm -rf "$dir"
        echo "   ✅ Removed: $dir"
    done
    PYTHON_CLEANED=1
fi

# Remove __pycache__ directories
PYCACHE_DIRS=$(find . -type d -name "__pycache__" 2>/dev/null || true)
if [ -n "$PYCACHE_DIRS" ]; then
    echo "$PYCACHE_DIRS" | while read -r dir; do
        rm -rf "$dir"
        echo "   ✅ Removed: $dir"
    done
    PYTHON_CLEANED=1
fi

# Remove .pyc files
PYC_FILES=$(find . -type f -name "*.pyc" -o -name "*.pyo" 2>/dev/null || true)
if [ -n "$PYC_FILES" ]; then
    echo "$PYC_FILES" | while read -r file; do
        rm -f "$file"
        echo "   ✅ Removed: $file"
    done
    PYTHON_CLEANED=1
fi

if [ $PYTHON_CLEANED -eq 0 ]; then
    echo "   ✅ No Python artifacts found"
fi
echo ""

# 5. Remove Node.js artifacts
echo "5. Removing Node.js artifacts..."
NODE_CLEANED=0

# Remove node_modules directories
NODE_MODULES=$(find . -type d -name "node_modules" 2>/dev/null || true)
if [ -n "$NODE_MODULES" ]; then
    echo "$NODE_MODULES" | while read -r dir; do
        rm -rf "$dir"
        echo "   ✅ Removed: $dir"
    done
    NODE_CLEANED=1
fi

# Remove package-lock.json, yarn.lock (optional - commented out to keep dependency locks)
# find . -name "package-lock.json" -o -name "yarn.lock" 2>/dev/null | xargs rm -f

if [ $NODE_CLEANED -eq 0 ]; then
    echo "   ✅ No Node.js artifacts found"
fi
echo ""

# 6. Remove Istio files
echo "6. Removing Istio files..."
ISTIO_CLEANED=0

# Remove Istio-related YAML files and directories
ISTIO_FILES=$(find . -type f -name "*istio*.yaml" -o -name "*istio*.yml" 2>/dev/null || true)
if [ -n "$ISTIO_FILES" ]; then
    echo "$ISTIO_FILES" | while read -r file; do
        rm -f "$file"
        echo "   ✅ Removed: $file"
    done
    ISTIO_CLEANED=1
fi

ISTIO_DIRS=$(find . -type d -name "istio" 2>/dev/null || true)
if [ -n "$ISTIO_DIRS" ]; then
    echo "$ISTIO_DIRS" | while read -r dir; do
        rm -rf "$dir"
        echo "   ✅ Removed: $dir"
    done
    ISTIO_CLEANED=1
fi

if [ $ISTIO_CLEANED -eq 0 ]; then
    echo "   ✅ No Istio files found"
fi
echo ""

# 7. Remove log files
echo "7. Removing log files..."
LOGS=$(find . -type f -name "*.log" -o -name "server.log" 2>/dev/null || true)
if [ -n "$LOGS" ]; then
    echo "$LOGS" | while read -r file; do
        rm -f "$file"
        echo "   ✅ Removed: $file"
    done
else
    echo "   ✅ No log files found"
fi
echo ""

# 8. Check for API keys and secrets
echo "8. Checking for API keys and secrets..."
API_KEY_PATTERNS=(
    "api[_-]?key"
    "apikey"
    "secret"
    "password"
    "token"
    "credential"
    "access[_-]?key"
)

FOUND_KEYS=0
for pattern in "${API_KEY_PATTERNS[@]}"; do
    # Search for potential API keys (long alphanumeric strings after key names)
    RESULTS=$(grep -r -i -E "${pattern}.*=.*['\"][a-zA-Z0-9]{16,}" . --include="*.java" --include="*.js" --include="*.py" --include="*.sh" --include="*.yaml" --include="*.yml" --include="*.properties" --include="*.env" 2>/dev/null || true)
    if [ -n "$RESULTS" ]; then
        echo "   ⚠️  WARNING: Potential API keys found matching pattern: $pattern"
        echo "$RESULTS" | head -5 | while read -r line; do
            echo "      $line"
        done
        FOUND_KEYS=1
    fi
done

if [ $FOUND_KEYS -eq 0 ]; then
    echo "   ✅ No API keys or secrets detected"
else
    echo "   ⚠️  Please manually review and remove any API keys found above"
fi
echo ""

echo "=========================================="
echo "✅ Cleanup complete!"
echo ""
echo "Summary:"
echo "  - Java services: Stopped"
echo "  - Docker resources: Cleaned"
echo "  - Build artifacts: Removed"
echo "  - Python artifacts: Removed"
echo "  - Node.js artifacts: Removed"
echo "  - Istio files: Removed"
echo "  - Log files: Removed"
