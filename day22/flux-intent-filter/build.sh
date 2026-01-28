#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Building Flux Intent Filter..."
"$SCRIPT_DIR/mvnw" clean compile -q
echo "✅ Build complete"
