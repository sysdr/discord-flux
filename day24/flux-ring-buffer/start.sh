#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Building project..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo "🚀 Starting Gateway and Dashboard..."
mvn exec:java -Dexec.mainClass="com.flux.ringbuffer.Main" -q
