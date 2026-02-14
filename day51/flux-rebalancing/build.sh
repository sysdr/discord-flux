#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "📦 Building Flux Rebalancing Simulator..."
javac --release 21 \
    -d target/classes \
    -sourcepath src/main/java \
    src/main/java/com/flux/rebalancing/*.java

if [ $? -eq 0 ]; then
    echo "✅ Build successful (target/classes)"
else
    echo "❌ Build failed"
    exit 1
fi
