#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

echo "🧹 Starting cleanup process..."
echo ""

# Stop Flux Gateway services
echo "🛑 Stopping Flux Gateway services..."
pkill -f FluxGateway 2>/dev/null || echo "  No FluxGateway processes found"
pkill -f LoadTest 2>/dev/null || echo "  No LoadTest processes found"
sleep 2

# Stop all Docker containers
echo "🐳 Stopping Docker containers..."
if command -v docker &> /dev/null; then
    if docker ps -q 2>/dev/null | head -1 | grep -q .; then
        echo "  Stopping running containers..."
        docker stop $(docker ps -q) 2>/dev/null || true
    else
        echo "  No running containers found"
    fi
    
    # Remove all stopped containers
    if docker ps -aq 2>/dev/null | head -1 | grep -q .; then
        echo "  Removing stopped containers..."
        docker rm $(docker ps -aq) 2>/dev/null || true
    fi
    
    # Remove unused Docker resources
    echo "  Cleaning up unused Docker resources..."
    docker system prune -af --volumes 2>/dev/null || true
    
    echo "  ✅ Docker cleanup complete"
else
    echo "  ⚠️  Docker not installed or not accessible"
fi

# Remove node_modules directories
echo ""
echo "📦 Removing node_modules directories..."
find . -type d -name "node_modules" -prune -exec rm -rf {} \; 2>/dev/null || true
NODE_COUNT=$(find . -type d -name "node_modules" 2>/dev/null | wc -l)
if [ "$NODE_COUNT" -eq 0 ]; then
    echo "  ✅ No node_modules directories found"
else
    echo "  ✅ Removed $NODE_COUNT node_modules directories"
fi

# Remove Python virtual environments
echo ""
echo "🐍 Removing Python virtual environments..."
find . -type d -name "venv" -prune -exec rm -rf {} \; 2>/dev/null || true
find . -type d -name ".venv" -prune -exec rm -rf {} \; 2>/dev/null || true
find . -type d -name "env" -prune -exec rm -rf {} \; 2>/dev/null || true
VENV_COUNT=$(find . -type d \( -name "venv" -o -name ".venv" -o -name "env" \) 2>/dev/null | wc -l)
if [ "$VENV_COUNT" -eq 0 ]; then
    echo "  ✅ No virtual environments found"
else
    echo "  ✅ Removed $VENV_COUNT virtual environments"
fi

# Remove pytest cache
echo ""
echo "🧪 Removing pytest cache..."
find . -type d -name ".pytest_cache" -prune -exec rm -rf {} \; 2>/dev/null || true
PYTEST_COUNT=$(find . -type d -name ".pytest_cache" 2>/dev/null | wc -l)
if [ "$PYTEST_COUNT" -eq 0 ]; then
    echo "  ✅ No pytest cache found"
else
    echo "  ✅ Removed $PYTEST_COUNT pytest cache directories"
fi

# Remove Python cache files
echo ""
echo "🐍 Removing Python cache files..."
find . -type d -name "__pycache__" -prune -exec rm -rf {} \; 2>/dev/null || true
find . -type f -name "*.pyc" -delete 2>/dev/null || true
find . -type f -name "*.pyo" -delete 2>/dev/null || true
find . -type f -name "*.pyd" -delete 2>/dev/null || true
PYC_COUNT=$(find . -type f \( -name "*.pyc" -o -name "*.pyo" -o -name "*.pyd" \) 2>/dev/null | wc -l)
if [ "$PYC_COUNT" -eq 0 ]; then
    echo "  ✅ No Python cache files found"
else
    echo "  ✅ Removed $PYC_COUNT Python cache files"
fi

# Remove Istio files
echo ""
echo "🛡️  Removing Istio files..."
find . -type f -name "*istio*" -delete 2>/dev/null || true
find . -type d -name "*istio*" -prune -exec rm -rf {} \; 2>/dev/null || true
ISTIO_COUNT=$(find . -path "*istio*" 2>/dev/null | wc -l)
if [ "$ISTIO_COUNT" -eq 0 ]; then
    echo "  ✅ No Istio files found"
else
    echo "  ✅ Removed $ISTIO_COUNT Istio files"
fi

# Remove Maven target directories (keep in project, but clean them)
echo ""
echo "📦 Cleaning Maven target directories..."
find . -type d -name "target" -path "*/flux-zombie-reaper/target" -exec rm -rf {} \; 2>/dev/null || true
MAVEN_COUNT=$(find . -type d -name "target" -path "*/flux-zombie-reaper/target" 2>/dev/null | wc -l)
if [ "$MAVEN_COUNT" -eq 0 ]; then
    echo "  ✅ No Maven target directories found"
else
    echo "  ✅ Cleaned $MAVEN_COUNT Maven target directories"
fi

# Remove log files
echo ""
echo "📝 Removing log files..."
find . -maxdepth 1 -type f -name "*.log" -delete 2>/dev/null || true
rm -f /tmp/flux-gateway.log /tmp/flux-server.log 2>/dev/null || true
echo "  ✅ Log files removed"

# Remove temporary files
echo ""
echo "🗑️  Removing temporary files..."
find . -type f -name ".DS_Store" -delete 2>/dev/null || true
find . -type f -name "*.swp" -delete 2>/dev/null || true
find . -type f -name "*.swo" -delete 2>/dev/null || true
find . -type f -name "*~" -delete 2>/dev/null || true
echo "  ✅ Temporary files removed"

# Check for API keys or secrets
echo ""
echo "🔍 Checking for API keys and secrets..."
API_KEY_FILES=$(grep -r -l -i -E "(api[_-]?key|apikey|secret|password|token)" --include="*.sh" --include="*.py" --include="*.java" --include="*.js" --include="*.ts" --include="*.yml" --include="*.yaml" --include="*.json" --include="*.properties" . 2>/dev/null || true)

if [ -z "$API_KEY_FILES" ]; then
    echo "  ✅ No API keys found in code files"
else
    echo "  ⚠️  Found potential API key references in:"
    echo "$API_KEY_FILES" | while read -r file; do
        echo "    - $file"
    done
    echo "  💡 Please review these files manually"
fi

echo ""
echo "✅ Cleanup complete!"
echo ""
echo "📋 Summary:"
echo "  • Stopped Flux Gateway services"
echo "  • Stopped and removed Docker containers"
echo "  • Removed node_modules, venv, pytest cache"
echo "  • Removed Python cache files"
echo "  • Removed Istio files"
echo "  • Cleaned Maven target directories"
echo "  • Removed log and temporary files"
echo ""
