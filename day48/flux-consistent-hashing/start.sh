#!/bin/bash

set -e

echo "=== Building Flux Consistent Hashing Demo ==="

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "❌ Error: Java 21+ required (found Java $JAVA_VERSION)"
    exit 1
fi

# Free port 8080 if already in use by a previous demo
if command -v lsof &>/dev/null; then
    PID=$(lsof -ti :8080 2>/dev/null || true)
    if [ -n "$PID" ]; then
        echo "Stopping existing demo on port 8080 (PID $PID)..."
        kill $PID 2>/dev/null || true
        sleep 2
    fi
fi

# Compile
if command -v mvn &> /dev/null; then
    echo "Using Maven..."
    mvn clean compile -q
    
    # Run
    echo ""
    mvn exec:java -Dexec.mainClass="com.flux.gateway.hashing.ConsistentHashingDemo" -q
else
    echo "Using javac..."
    # Compile all source files
    find src/main/java -name "*.java" -print0 | xargs -0 javac -d target/classes --enable-preview
    
    # Run
    echo ""
    java --enable-preview -cp target/classes com.flux.gateway.hashing.ConsistentHashingDemo
fi
