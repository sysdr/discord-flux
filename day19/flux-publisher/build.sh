#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Building Flux Publisher..."

mvn clean compile test-compile -q
mvn test -q

echo ""
echo "✅ Build complete!"
