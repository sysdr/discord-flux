#!/bin/bash
cd "$(dirname "$0")"

echo "🧹 Cleaning up Flux Migration..."

# Kill running processes
if [ -f .pids ]; then
    while read pid; do
        kill $pid 2>/dev/null || true
    done < .pids
    rm .pids
fi

# Clean Maven artifacts
mvn clean -q 2>/dev/null || true

# Remove checkpoint
rm -f migration.checkpoint migration.checkpoint.tmp

echo "✅ Cleanup complete"
