#!/bin/bash

set -e

echo "=== Building Flux Consistent Hashing Demo ==="

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "❌ Error: Java 21+ required (found Java $JAVA_VERSION)"
    exit 1
fi

if command -v mvn &> /dev/null; then
    echo "Using Maven..."
    mvn clean compile -q
    echo "✓ Build complete (target/classes)"
else
    echo "Using javac..."
    mkdir -p target/classes
    find src/main/java -name "*.java" -print0 | xargs -0 javac -d target/classes --release 21 --enable-preview
    echo "✓ Build complete (target/classes)"
fi
