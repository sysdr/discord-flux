#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 Starting Flux Rebalancing Simulator..."

# Compile Java files
echo "📦 Compiling..."
javac --release 21 \
    -d target/classes \
    -sourcepath src/main/java \
    src/main/java/com/flux/rebalancing/*.java

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"
echo "🏃 Starting simulator..."
echo ""

# Run simulator
java -cp target/classes \
    --enable-preview \
    com.flux.rebalancing.RebalancingSimulator
