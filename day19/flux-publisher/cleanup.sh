#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🧹 Cleaning up Flux Publisher..."

# Kill running processes
pkill -f "com.flux.publisher.PublisherApp" || true

# Clean Maven artifacts
mvn clean -q > /dev/null 2>&1 || true

# Clear Redis test data
redis-cli KEYS "guild:*:messages" | xargs redis-cli DEL > /dev/null 2>&1 || true

# Remove logs
rm -rf logs/*.log

echo "✅ Cleanup complete"
