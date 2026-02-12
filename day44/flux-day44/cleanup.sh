#!/usr/bin/env bash
cd "$(dirname "$0")"
echo "==> Cleaning up Flux Day 44..."

# Kill any running instances
pkill -f "flux-day44-readstate" 2>/dev/null && echo "  Stopped running server" || echo "  No running server found"

# Maven clean
mvn -q clean 2>/dev/null && echo "  Maven artifacts cleaned" || true

# Remove temp files
find . -name "*.class" -delete 2>/dev/null || true
find . -name "hs_err_pid*.log" -delete 2>/dev/null || true

echo "  Done."
